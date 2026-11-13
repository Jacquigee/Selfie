package com.jacqui.selfie.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacqui.selfie.ui.components.EmotionBadgeCard
import com.jacqui.selfie.ui.components.FaceMeshOverlay
import com.jacqui.selfie.ui.components.SpeakerTelemetryHud
import com.jacqui.selfie.ui.viewmodel.EmotionViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Primary screen for the **Real-Time Emotion Detector** presentation demo.
 *
 * Orchestrates:
 * 1. Runtime `CAMERA` permission acquisition with a clear rationale screen.
 * 2. Lifecycle-safe CameraX binding via [LocalLifecycleOwner].
 * 3. Declarative `CameraXViewfinder` rendering with `ContentScale.Crop` (`FILL_CENTER`).
 * 4. Real-time [FaceMeshOverlay] aligned via `CoordinateMapper`.
 * 5. Interactive top control bar, [SpeakerTelemetryHud], and [EmotionBadgeCard].
 */
@Composable
fun EmotionDetectorScreen(
    modifier: Modifier = Modifier,
    viewModel: EmotionViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Takeaway #1: Lifecycle-Safe Camera Setup — binds automatically when permission is granted
    LaunchedEffect(hasCameraPermission, lifecycleOwner) {
        if (hasCameraPermission) {
            viewModel.bindCamera(lifecycleOwner)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090C10))
    ) {
        if (!hasCameraPermission) {
            CameraPermissionRationaleCard(
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            // 1. Live CameraX Compose Viewfinder (Center-Cropped by default to match CoordinateMapper)
            uiState.surfaceRequest?.let { request ->
                CameraXViewfinder(
                    surfaceRequest = request,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 2. Real-time 478-Point Face Mesh Overlay (Takeaway #4: Coordinate Mapping)
            if (uiState.showFaceMesh && uiState.analysisResult.faceDetected) {
                FaceMeshOverlay(
                    landmarks = uiState.analysisResult.landmarks,
                    sourceWidth = uiState.analysisResult.sourceWidth,
                    sourceHeight = uiState.analysisResult.sourceHeight,
                    isFrontCamera = uiState.analysisResult.isFrontCamera,
                    dominantEmotion = uiState.analysisResult.dominantEmotion,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 3. Foreground HUD Controls & Emotion Feedback
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar + Collapsible Speaker Telemetry HUD
                Column(modifier = Modifier.fillMaxWidth()) {
                    TopDemoControlBar(
                        showTelemetryHud = uiState.showTelemetryHud,
                        showFaceMesh = uiState.showFaceMesh,
                        onToggleHud = viewModel::toggleTelemetryHud,
                        onToggleMesh = viewModel::toggleFaceMesh,
                        onSwitchCamera = { viewModel.toggleCameraLens(lifecycleOwner) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SpeakerTelemetryHud(
                        visible = uiState.showTelemetryHud,
                        fps = uiState.analysisResult.frameFps,
                        inferenceTimeMs = uiState.analysisResult.inferenceTimeMs,
                        sourceWidth = uiState.analysisResult.sourceWidth,
                        sourceHeight = uiState.analysisResult.sourceHeight,
                        activeDelegate = uiState.analysisResult.activeDelegate,
                        showFaceMesh = uiState.showFaceMesh,
                        topBlendshapes = uiState.analysisResult.topBlendshapes,
                        errorMessage = uiState.errorMessage,
                        onToggleFaceMesh = viewModel::toggleFaceMesh,
                        onToggleDelegate = viewModel::toggleInferenceDelegate
                    )
                }

                // Bottom Expressive Emotion Badge Card
                EmotionBadgeCard(
                    dominantEmotion = uiState.analysisResult.dominantEmotion,
                    confidence = uiState.analysisResult.confidence,
                    emotionScores = uiState.analysisResult.emotionScores,
                    faceDetected = uiState.analysisResult.faceDetected
                )
            }
        }
    }
}

@Composable
private fun TopDemoControlBar(
    showTelemetryHud: Boolean,
    showFaceMesh: Boolean,
    onToggleHud: () -> Unit,
    onToggleMesh: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50)
            ),
        shape = RoundedCornerShape(50),
        color = Color(0xBF0D1117)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Selfie • Emotion AI",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                Text(
                    text = "CameraX + MediaPipe LiteRT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = Color(0xFF80DEEA)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onToggleMesh,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (showFaceMesh) Color(0xFF00E676).copy(alpha = 0.24f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Toggle Face Mesh Overlay",
                        tint = if (showFaceMesh) Color(0xFF00E676) else Color.White
                    )
                }

                IconButton(
                    onClick = onToggleHud,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (showTelemetryHud) Color(0xFF80DEEA).copy(alpha = 0.24f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Toggle Speaker Telemetry HUD",
                        tint = if (showTelemetryHud) Color(0xFF80DEEA) else Color.White
                    )
                }

                IconButton(
                    onClick = onSwitchCamera,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Front/Back Camera",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionRationaleCard(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(24.dp)
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF141A24)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📷",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Camera Access Required",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Selfie analyzes facial landmarks and 52 FACS blendshapes 100% on-device using Jetpack CameraX and MediaPipe LiteRT. No frames ever leave your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB0BEC5),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Grant Camera Permission",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}
