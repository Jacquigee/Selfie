package com.jacqui.selfie.ui.viewmodel

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacqui.selfie.data.analyzer.EmotionImageAnalyzer
import com.jacqui.selfie.data.camera.CameraSessionManager
import com.jacqui.selfie.domain.model.InferenceDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Presentation-layer state holder that coordinates [CameraSessionManager] and
 * [EmotionImageAnalyzer] using Unidirectional Data Flow (UDF).
 *
 * Exposes a single consolidated [uiState] [StateFlow] collected lifecycle-safely by Compose via
 * `collectAsStateWithLifecycle()`.
 */
class EmotionViewModel(
    private val cameraSessionManager: CameraSessionManager,
    private val emotionImageAnalyzer: EmotionImageAnalyzer
) : ViewModel() {

    private val isFrontCameraFlow = MutableStateFlow(true)
    private val showFaceMeshFlow = MutableStateFlow(true)
    private val showTelemetryHudFlow = MutableStateFlow(true)

    /**
     * Consolidated reactive UI state combining the CameraX [SurfaceRequest], MediaPipe
     * [com.jacqui.selfie.domain.model.FaceAnalysisResult], and interactive presentation toggles.
     */
    val uiState: StateFlow<EmotionUiState> = combine(
        cameraSessionManager.surfaceRequest,
        emotionImageAnalyzer.analysisResult,
        isFrontCameraFlow,
        showFaceMeshFlow,
        showTelemetryHudFlow,
        emotionImageAnalyzer.errorMessage
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        EmotionUiState(
            surfaceRequest = values[0] as androidx.camera.core.SurfaceRequest?,
            analysisResult = values[1] as com.jacqui.selfie.domain.model.FaceAnalysisResult,
            isFrontCamera = values[2] as Boolean,
            showFaceMesh = values[3] as Boolean,
            showTelemetryHud = values[4] as Boolean,
            errorMessage = values[5] as String?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EmotionUiState()
    )

    /**
     * Binds the CameraX pipeline (`Preview` + `ImageAnalysis`) to the active [LifecycleOwner].
     */
    fun bindCamera(lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch {
            cameraSessionManager.bindCameraUseCases(
                lifecycleOwner = lifecycleOwner,
                isFrontCamera = isFrontCameraFlow.value
            )
        }
    }

    /**
     * Flips between the front selfie camera and the rear camera and rebinds to [lifecycleOwner].
     */
    fun toggleCameraLens(lifecycleOwner: LifecycleOwner) {
        val nextFront = !isFrontCameraFlow.value
        isFrontCameraFlow.value = nextFront
        viewModelScope.launch {
            cameraSessionManager.bindCameraUseCases(
                lifecycleOwner = lifecycleOwner,
                isFrontCamera = nextFront
            )
        }
    }

    /**
     * Toggles the visibility of the 478-point facial landmark mesh overlay.
     */
    fun toggleFaceMesh() {
        showFaceMeshFlow.value = !showFaceMeshFlow.value
    }

    /**
     * Toggles the Speaker Telemetry HUD panel during the live presentation demo.
     */
    fun toggleTelemetryHud() {
        showTelemetryHudFlow.value = !showTelemetryHudFlow.value
    }

    /**
     * Switches MediaPipe inference between `Delegate.CPU` and `Delegate.GPU`.
     */
    fun toggleInferenceDelegate() {
        val current = uiState.value.analysisResult.activeDelegate
        val next = if (current == InferenceDelegate.CPU) {
            InferenceDelegate.GPU
        } else {
            InferenceDelegate.CPU
        }
        viewModelScope.launch {
            emotionImageAnalyzer.initializeFaceLandmarker(next)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraSessionManager.release()
    }
}
