package com.jacqui.selfie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.jacqui.selfie.ui.screen.EmotionDetectorScreen
import com.jacqui.selfie.ui.theme.SelfieTheme

/**
 * Single-Activity host for the **Real-Time Emotion Detector with CameraX** presentation demo.
 *
 * Enables edge-to-edge rendering so the `CameraXViewfinder` and `FaceMeshOverlay` span the
 * entire display while HUD controls respect system insets via `Modifier.safeDrawingPadding()`.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SelfieTheme(darkTheme = true, dynamicColor = false) {
                EmotionDetectorScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
