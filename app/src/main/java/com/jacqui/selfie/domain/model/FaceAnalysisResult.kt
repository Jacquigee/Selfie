package com.jacqui.selfie.domain.model

/**
 * Represents a single 3D facial landmark normalized to `[0.0f .. 1.0f]` within the
 * upright analyzed camera frame.
 *
 * MediaPipe Face Landmarker outputs 478 normalized points per detected face:
 * - [x]: Horizontal coordinate in `[0f..1f]` from left to right of the upright source image.
 * - [y]: Vertical coordinate in `[0f..1f]` from top to bottom of the upright source image.
 * - [z]: Relative depth coordinate (with the head center of mass as the origin).
 */
data class NormalizedPoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f
)

/**
 * Represents an individual facial blendshape activation score produced by MediaPipe.
 *
 * @property name The ARKit/MediaPipe blendshape category name (e.g., `"mouthSmileLeft"`, `"jawOpen"`).
 * @property score Activation probability in `[0.0f .. 1.0f]`.
 */
data class BlendshapeMetric(
    val name: String,
    val score: Float
)

/**
 * Hardware delegate options for LiteRT / MediaPipe inference.
 */
enum class InferenceDelegate(val label: String) {
    CPU("CPU (LiteRT XNNPACK)"),
    GPU("GPU (OpenGL / OpenCL)")
}

/**
 * Immutable domain snapshot emitted for every camera frame processed by the ML pipeline.
 *
 * Combines semantic emotion classification, geometric face landmarks for the UI overlay,
 * and real-time performance telemetry for the Speaker HUD.
 *
 * @property dominantEmotion Top classified [Emotion] after EMA temporal smoothing.
 * @property confidence Smoothed activation score (`0.0f..1.0f`) of [dominantEmotion].
 * @property emotionScores Map of all candidate [Emotion] states to their smoothed scores.
 * @property topBlendshapes The highest-scoring raw blendshapes in the current frame (for telemetry).
 * @property landmarks 478 normalized facial landmarks in upright image coordinates.
 * @property inferenceTimeMs End-to-end MediaPipe inference latency in milliseconds.
 * @property frameFps Rolling frames-per-second throughput of the analysis pipeline.
 * @property sourceWidth Width of the upright analyzed frame in pixels (after sensor rotation).
 * @property sourceHeight Height of the upright analyzed frame in pixels (after sensor rotation).
 * @property isFrontCamera Whether the frame originated from the front-facing selfie camera.
 * @property activeDelegate Which hardware delegate executed the inference.
 * @property faceDetected `true` if at least one face was detected in the frame.
 */
data class FaceAnalysisResult(
    val dominantEmotion: Emotion = Emotion.NEUTRAL,
    val confidence: Float = 1.0f,
    val emotionScores: Map<Emotion, Float> = emptyMap(),
    val topBlendshapes: List<BlendshapeMetric> = emptyList(),
    val landmarks: List<NormalizedPoint> = emptyList(),
    val inferenceTimeMs: Long = 0L,
    val frameFps: Int = 0,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val isFrontCamera: Boolean = true,
    val activeDelegate: InferenceDelegate = InferenceDelegate.CPU,
    val faceDetected: Boolean = false
)
