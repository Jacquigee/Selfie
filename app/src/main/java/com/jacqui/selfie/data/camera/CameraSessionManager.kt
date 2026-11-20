package com.jacqui.selfie.data.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.jacqui.selfie.data.analyzer.EmotionImageAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * **Presentation Takeaway #1: Lifecycle-Safe Camera Setup & Backpressure**
 *
 * Manages the Jetpack CameraX pipeline ([Preview] + [ImageAnalysis]) and exposes a
 * Compose-ready [SurfaceRequest] flow consumed directly by `CameraXViewfinder`.
 *
 * ### Architectural Highlights for the Talk:
 * 1. **Compose-Native `SurfaceRequest`**:
 *    Instead of managing legacy `SurfaceView`/`TextureView` callbacks manually, `Preview`
 *    publishes a [SurfaceRequest] into [_surfaceRequest], which `CameraXViewfinder` renders
 *    declaratively inside Jetpack Compose.
 * 2. **Non-Blocking Backpressure (`STRATEGY_KEEP_ONLY_LATEST`)**:
 *    If MediaPipe takes longer than 33ms on a lower-end device, CameraX drops intermediate
 *    frames and always hands the analyzer the freshest frame—preventing latency buildup and
 *    out-of-memory queues.
 * 3. **Resolution Tuning (640x480 target for ML)**:
 *    Face landmarking and blendshape networks internally resize inputs to 256x256. Feeding
 *    4K frames wastes memory bandwidth and battery. Requesting a 4:3 ~640x480 analysis stream
 *    maximizes FPS and minimizes thermal throttling (Takeaway #5).
 */
class CameraSessionManager(
    private val appContext: Context,
    private val emotionImageAnalyzer: EmotionImageAnalyzer
) {

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)

    /**
     * Reactive [SurfaceRequest] observed by `CameraXViewfinder` in the Compose UI.
     */
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    /**
     * Dedicated single-thread background executor for [ImageAnalysis] frame copying and
     * MediaPipe `detectAsync` submission—keeping `Dispatchers.Main` 100% free for 60/120Hz UI.
     */
    private var analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * Binds the CameraX [Preview] and [ImageAnalysis] use cases to the given [lifecycleOwner].
     *
     * Because CameraX is lifecycle-aware, camera hardware automatically opens when the
     * [lifecycleOwner] starts and releases when the [lifecycleOwner] stops—with zero manual
     * `onResume`/`onPause` boilerplate.
     *
     * @param lifecycleOwner The active Compose/Activity [LifecycleOwner].
     * @param isFrontCamera `true` for the front selfie camera, `false` for the rear camera.
     */
    suspend fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        isFrontCamera: Boolean = true
    ) {
        try {
            if (analysisExecutor.isShutdown) {
                analysisExecutor = Executors.newSingleThreadExecutor()
            }

            val provider = ProcessCameraProvider.getInstance(appContext).await()
            cameraProvider = provider

            emotionImageAnalyzer.setFrontCamera(isFrontCamera)

            // 4:3 aspect ratio matches standard selfie sensor native geometry
            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            // 1. Preview UseCase: feeds the Compose CameraXViewfinder via SurfaceRequest
            val previewUseCase = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .apply {
                    setSurfaceProvider { request ->
                        _surfaceRequest.value = request
                    }
                }

            // 2. ImageAnalysis UseCase: streams RGBA_8888 frames with KEEP_ONLY_LATEST backpressure
            val imageAnalysisUseCase = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .apply {
                    setAnalyzer(analysisExecutor, emotionImageAnalyzer)
                }

            // Select requested lens with graceful fallback if device only has one camera (e.g., emulator)
            val preferredSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val activeSelector = if (provider.hasCamera(preferredSelector)) {
                preferredSelector
            } else {
                Log.w(TAG, "Preferred lens unavailable; falling back to available camera")
                if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    emotionImageAnalyzer.setFrontCamera(true)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    emotionImageAnalyzer.setFrontCamera(false)
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                activeSelector,
                previewUseCase,
                imageAnalysisUseCase
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind CameraX use cases", e)
        }
    }

    /**
     * Unbinds all camera use cases and shuts down the background executor when the ViewModel clears.
     */
    fun release() {
        cameraProvider?.unbindAll()
        emotionImageAnalyzer.close()
        analysisExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraSessionManager"
    }
}
