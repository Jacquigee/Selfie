package com.jacqui.selfie.data.analyzer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.jacqui.selfie.domain.classifier.BlendshapeEmotionClassifier
import com.jacqui.selfie.domain.model.BlendshapeMetric
import com.jacqui.selfie.domain.model.Emotion
import com.jacqui.selfie.domain.model.FaceAnalysisResult
import com.jacqui.selfie.domain.model.InferenceDelegate
import com.jacqui.selfie.domain.model.NormalizedPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * **Presentation Takeaway #2: Buffer Hygiene & Takeaway #5: Production Performance**
 *
 * CameraX [ImageAnalysis.Analyzer] implementation that bridges live camera frames into
 * MediaPipe's [FaceLandmarker] (`RunningMode.LIVE_STREAM`) with zero UI-thread blocking.
 *
 * ### Key Production Practices Demonstrated:
 * 1. **Deterministic `ImageProxy` Closure (`.use {}`)**:
 *    CameraX maintains a bounded ring buffer of hardware `ImageReader` surfaces. If an
 *    [ImageProxy] is not closed on every code path, the camera pipeline stalls silently.
 *    Wrapping `imageProxy.use { ... }` guarantees `close()` runs even on early returns or exceptions.
 * 2. **Hardware-Friendly `RGBA_8888` Output**:
 *    By configuring `ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888`, CameraX's native pipeline
 *    delivers a single contiguous RGBA plane, avoiding slow CPU YUV-to-RGB loops in Kotlin.
 * 3. **Bitmap Buffer Reuse**:
 *    Allocating a new `Bitmap` 30 times per second causes GC pressure and frame drops ("memory churn").
 *    We reuse [reusableRawBitmap] across frames whenever sensor dimensions remain constant.
 * 4. **Sensor Rotation Normalization**:
 *    Front/back camera sensors are typically mounted at 270° or 90° relative to the display.
 *    We rotate the frame using [rotationMatrix] before calling `detectAsync` so MediaPipe's
 *    face detector sees an upright face and emits normalized `[0..1]` landmarks in upright space.
 */
class EmotionImageAnalyzer(
    private val context: Context,
    private val emotionClassifier: BlendshapeEmotionClassifier
) : ImageAnalysis.Analyzer {

    private val _analysisResult = MutableStateFlow(FaceAnalysisResult())

    /**
     * Reactive stream of the latest [FaceAnalysisResult] for the ViewModel and UI layer.
     */
    val analysisResult: StateFlow<FaceAnalysisResult> = _analysisResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    @Volatile
    private var faceLandmarker: FaceLandmarker? = null

    @Volatile
    private var isFrontCamera: Boolean = true

    @Volatile
    private var activeDelegate: InferenceDelegate = InferenceDelegate.CPU

    // Reusable bitmap buffer to eliminate per-frame heap allocations (Takeaway #5)
    private var reusableRawBitmap: Bitmap? = null
    private val rotationMatrix = Matrix()

    // Rolling window of frame timestamps (ms) for smooth instantaneous FPS calculation
    private val frameTimestamps = ArrayDeque<Long>(FPS_WINDOW_SIZE)

    init {
        initializeFaceLandmarker(InferenceDelegate.CPU)
    }

    /**
     * Configures or reconfigures the MediaPipe [FaceLandmarker] with the requested
     * [InferenceDelegate] (`CPU` or `GPU`). Automatically falls back to `CPU` if the
     * device or emulator does not support the OpenGL/OpenCL GPU delegate.
     */
    @Synchronized
    fun initializeFaceLandmarker(requestedDelegate: InferenceDelegate = activeDelegate) {
        try {
            faceLandmarker?.close()
            faceLandmarker = buildLandmarker(requestedDelegate)
            activeDelegate = requestedDelegate
            _errorMessage.value = null
            Log.i(TAG, "FaceLandmarker initialized with delegate: ${requestedDelegate.label}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize ${requestedDelegate.label}, falling back to CPU", e)
            if (requestedDelegate == InferenceDelegate.GPU) {
                try {
                    faceLandmarker = buildLandmarker(InferenceDelegate.CPU)
                    activeDelegate = InferenceDelegate.CPU
                    _errorMessage.value = "GPU delegate unavailable on this device; running on CPU."
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "CPU fallback also failed", fallbackEx)
                    _errorMessage.value = "Failed to initialize FaceLandmarker: ${fallbackEx.message}"
                }
            } else {
                _errorMessage.value = "Failed to initialize FaceLandmarker: ${e.message}"
            }
        }
    }

    private fun buildLandmarker(delegate: InferenceDelegate): FaceLandmarker {
        val mpDelegate = when (delegate) {
            InferenceDelegate.CPU -> Delegate.CPU
            InferenceDelegate.GPU -> Delegate.GPU
        }

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET_PATH)
            .setDelegate(mpDelegate)
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(MAX_FACES)
            .setMinFaceDetectionConfidence(MIN_DETECTION_CONFIDENCE)
            .setMinFacePresenceConfidence(MIN_PRESENCE_CONFIDENCE)
            .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
            // Essential for Takeaway #3: Enables the 52 ARKit facial expression blendshape scores
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(false)
            .setResultListener(::onFaceLandmarkerResult)
            .setErrorListener(::onFaceLandmarkerError)
            .build()

        return FaceLandmarker.createFromOptions(context, options)
    }

    /**
     * Updates whether the active camera stream is from the front-facing selfie lens.
     */
    fun setFrontCamera(isFront: Boolean) {
        if (this.isFrontCamera != isFront) {
            this.isFrontCamera = isFront
            emotionClassifier.reset()
        }
    }

    /**
     * Core CameraX callback invoked on the background analysis executor for every frame.
     *
     * **Takeaway #2 (Buffer Hygiene)**:
     * Notice how the entire method body is wrapped in `imageProxy.use { proxy -> ... }`.
     * Once pixel bytes are copied into [reusableRawBitmap], `proxy.close()` is called
     * automatically at the end of the block, freeing the hardware camera buffer immediately.
     */
    override fun analyze(imageProxy: ImageProxy) {
        val landmarker = faceLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }

        val frameTimestampMs = SystemClock.uptimeMillis()

        val uprightBitmap: Bitmap = imageProxy.use { proxy ->
            val width = proxy.width
            val height = proxy.height
            val rotationDegrees = proxy.imageInfo.rotationDegrees

            // Reuse existing ARGB_8888 Bitmap when dimensions match to prevent GC churn
            val rawBitmap = obtainReusableBitmap(width, height)
            val planeBuffer = proxy.planes[0].buffer
            planeBuffer.rewind()
            rawBitmap.copyPixelsFromBuffer(planeBuffer)

            // Rotate bitmap to upright display orientation if sensor is mounted at 90/180/270 deg
            if (rotationDegrees != 0) {
                rotationMatrix.reset()
                rotationMatrix.postRotate(rotationDegrees.toFloat())
                Bitmap.createBitmap(
                    rawBitmap,
                    0,
                    0,
                    width,
                    height,
                    rotationMatrix,
                    true
                )
            } else {
                rawBitmap
            }
        } // <-- imageProxy.close() is guaranteed to execute right here!

        try {
            val mpImage = BitmapImageBuilder(uprightBitmap).build()
            landmarker.detectAsync(mpImage, frameTimestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting frame to FaceLandmarker", e)
        }
    }

    /**
     * Asynchronous callback invoked by MediaPipe's `LIVE_STREAM` pipeline when a frame finishes.
     *
     * @param result Contains the 478 normalized 3D landmarks and 52 blendshape scores.
     * @param inputImage The [MPImage] that was analyzed (provides upright `width` and `height`).
     */
    private fun onFaceLandmarkerResult(result: FaceLandmarkerResult, inputImage: MPImage) {
        val nowMs = SystemClock.uptimeMillis()
        val inferenceLatencyMs = (nowMs - result.timestampMs()).coerceAtLeast(1L)
        val currentFps = recordFrameAndComputeFps(nowMs)

        val faceLandmarksList = result.faceLandmarks()
        if (faceLandmarksList.isEmpty()) {
            // No face in frame: reset EMA history and publish empty overlay with live telemetry
            emotionClassifier.reset()
            _analysisResult.value = FaceAnalysisResult(
                dominantEmotion = Emotion.NEUTRAL,
                confidence = 1.0f,
                emotionScores = emptyMap(),
                topBlendshapes = emptyList(),
                landmarks = emptyList(),
                inferenceTimeMs = inferenceLatencyMs,
                frameFps = currentFps,
                sourceWidth = inputImage.width,
                sourceHeight = inputImage.height,
                isFrontCamera = isFrontCamera,
                activeDelegate = activeDelegate,
                faceDetected = false
            )
            return
        }

        // Extract 478 normalized landmarks for the primary face
        val primaryFaceLandmarks = faceLandmarksList[0].map { landmark ->
            NormalizedPoint(
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z()
            )
        }

        // Extract 52 blendshape category scores (excluding "_neutral" baseline category for top bars)
        val blendshapesOptional = result.faceBlendshapes()
        val rawBlendshapeMap = mutableMapOf<String, Float>()
        val topMetrics = mutableListOf<BlendshapeMetric>()

        if (blendshapesOptional.isPresent && blendshapesOptional.get().isNotEmpty()) {
            val categories = blendshapesOptional.get()[0]
            for (category in categories) {
                val name = category.categoryName()
                val score = category.score()
                rawBlendshapeMap[name] = score
                if (name != "_neutral") {
                    topMetrics.add(BlendshapeMetric(name = name, score = score))
                }
            }
            topMetrics.sortByDescending { it.score }
        }

        // Classify emotion on-device using FACS blendshape weights + EMA smoothing (Takeaway #3)
        val classification = emotionClassifier.classify(rawBlendshapeMap)

        _analysisResult.value = FaceAnalysisResult(
            dominantEmotion = classification.dominantEmotion,
            confidence = classification.confidence,
            emotionScores = classification.allScores,
            topBlendshapes = topMetrics.take(TOP_BLENDSHAPE_COUNT),
            landmarks = primaryFaceLandmarks,
            inferenceTimeMs = inferenceLatencyMs,
            frameFps = currentFps,
            sourceWidth = inputImage.width,
            sourceHeight = inputImage.height,
            isFrontCamera = isFrontCamera,
            activeDelegate = activeDelegate,
            faceDetected = true
        )
    }

    private fun onFaceLandmarkerError(error: RuntimeException) {
        Log.e(TAG, "MediaPipe FaceLandmarker runtime error", error)
        _errorMessage.value = error.message ?: "FaceLandmarker inference error"
    }

    private fun obtainReusableBitmap(width: Int, height: Int): Bitmap {
        val current = reusableRawBitmap
        if (current != null && current.width == width && current.height == height) {
            return current
        }
        val created = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        reusableRawBitmap = created
        return created
    }

    @Synchronized
    private fun recordFrameAndComputeFps(nowMs: Long): Int {
        frameTimestamps.addLast(nowMs)
        while (frameTimestamps.size > FPS_WINDOW_SIZE) {
            frameTimestamps.removeFirst()
        }
        if (frameTimestamps.size < 2) return 0
        val elapsedMs = (frameTimestamps.last - frameTimestamps.first).coerceAtLeast(1L)
        return (((frameTimestamps.size - 1) * 1000L) / elapsedMs).toInt()
    }

    /**
     * Releases native MediaPipe / LiteRT resources when the session ends.
     */
    @Synchronized
    fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
    }

    companion object {
        private const val TAG = "EmotionImageAnalyzer"
        private const val MODEL_ASSET_PATH = "face_landmarker.task"
        private const val MAX_FACES = 1
        private const val MIN_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_PRESENCE_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f
        private const val TOP_BLENDSHAPE_COUNT = 6
        private const val FPS_WINDOW_SIZE = 20
    }
}
