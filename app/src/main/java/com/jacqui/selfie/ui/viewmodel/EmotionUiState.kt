package com.jacqui.selfie.ui.viewmodel

import androidx.camera.core.SurfaceRequest
import com.jacqui.selfie.domain.model.FaceAnalysisResult

/**
 * Immutable UI state consumed by `EmotionDetectorScreen` following Unidirectional Data Flow (UDF).
 *
 * @property surfaceRequest Active CameraX [SurfaceRequest] rendered by `CameraXViewfinder`.
 * @property analysisResult Latest facial emotion, 478 landmarks, blendshapes, and telemetry snapshot.
 * @property isFrontCamera `true` if the front selfie camera is active, `false` for the rear camera.
 * @property showFaceMesh `true` if the 478-point Compose `Canvas` overlay should be rendered.
 * @property showTelemetryHud `true` if the Speaker Telemetry HUD overlay is expanded.
 * @property errorMessage Optional non-fatal status or delegate fallback warning.
 */
data class EmotionUiState(
    val surfaceRequest: SurfaceRequest? = null,
    val analysisResult: FaceAnalysisResult = FaceAnalysisResult(),
    val isFrontCamera: Boolean = true,
    val showFaceMesh: Boolean = true,
    val showTelemetryHud: Boolean = true,
    val errorMessage: String? = null
)
