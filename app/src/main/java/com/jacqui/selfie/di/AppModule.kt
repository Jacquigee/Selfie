package com.jacqui.selfie.di

import com.jacqui.selfie.data.analyzer.EmotionImageAnalyzer
import com.jacqui.selfie.data.camera.CameraSessionManager
import com.jacqui.selfie.domain.classifier.BlendshapeEmotionClassifier
import com.jacqui.selfie.ui.viewmodel.EmotionViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin Dependency Injection module wiring the domain classifier, MediaPipe frame analyzer,
 * CameraX session manager, and presentation ViewModel.
 */
val appModule = module {
    // Domain Layer: Pure FACS blendshape -> Emotion classifier with EMA temporal smoothing
    single { BlendshapeEmotionClassifier() }

    // Data / ML Layer: MediaPipe FaceLandmarker LIVE_STREAM analyzer
    single {
        EmotionImageAnalyzer(
            context = androidContext(),
            emotionClassifier = get()
        )
    }

    // Data / Camera Layer: Lifecycle-safe CameraX Preview + ImageAnalysis session manager
    single {
        CameraSessionManager(
            appContext = androidContext(),
            emotionImageAnalyzer = get()
        )
    }

    // Presentation Layer: UDF ViewModel
    viewModel {
        EmotionViewModel(
            cameraSessionManager = get(),
            emotionImageAnalyzer = get()
        )
    }
}
