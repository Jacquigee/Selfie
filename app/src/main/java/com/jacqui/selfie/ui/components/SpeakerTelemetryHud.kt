package com.jacqui.selfie.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jacqui.selfie.domain.model.BlendshapeMetric
import com.jacqui.selfie.domain.model.InferenceDelegate
import kotlin.math.roundToInt

/**
 * **Presentation Takeaway #5: Production Performance & Live Speaker Telemetry HUD**
 *
 * Interactive collapsible overlay designed for live stage demonstrations.
 * Displays:
 * - Instantaneous analysis throughput (**FPS**) and MediaPipe inference latency (**ms**).
 * - Upright sensor resolution and backpressure policy (`STRATEGY_KEEP_ONLY_LATEST`).
 * - Live top 6 raw ARKit/MediaPipe blendshape activation bars (`0.00 .. 1.00`).
 * - Interactive chips to toggle the 478-point Face Mesh overlay and switch between
 *   `Delegate.CPU` and `Delegate.GPU` live on stage.
 */
@Composable
fun SpeakerTelemetryHud(
    visible: Boolean,
    fps: Int,
    inferenceTimeMs: Long,
    sourceWidth: Int,
    sourceHeight: Int,
    activeDelegate: InferenceDelegate,
    showFaceMesh: Boolean,
    topBlendshapes: List<BlendshapeMetric>,
    errorMessage: String?,
    onToggleFaceMesh: () -> Unit,
    onToggleDelegate: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
        exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(20.dp)
                ),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xCC0D1117)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Header Row: Pipeline Telemetry Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SPEAKER TELEMETRY HUD",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = Color(0xFF80DEEA)
                    )

                    Text(
                        text = "KEEP_ONLY_LATEST • RGBA_8888",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color(0xFF90A4AE)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Key Performance Metrics (FPS, Latency, Resolution)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val fpsColor = when {
                        fps >= 24 -> Color(0xFF00E676)
                        fps >= 15 -> Color(0xFFFFEA00)
                        else -> Color(0xFFFF5252)
                    }
                    TelemetryMetricPill(
                        label = "FPS",
                        value = "$fps",
                        valueColor = fpsColor,
                        modifier = Modifier.weight(1f)
                    )

                    val latencyColor = when {
                        inferenceTimeMs <= 35L -> Color(0xFF00E676)
                        inferenceTimeMs <= 65L -> Color(0xFFFFEA00)
                        else -> Color(0xFFFF5252)
                    }
                    TelemetryMetricPill(
                        label = "INFERENCE",
                        value = "${inferenceTimeMs}ms",
                        valueColor = latencyColor,
                        modifier = Modifier.weight(1f)
                    )

                    TelemetryMetricPill(
                        label = "FRAME",
                        value = if (sourceWidth > 0) "${sourceWidth}x${sourceHeight}" else "--",
                        valueColor = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Demo Toggles (Face Mesh Overlay & Hardware Delegate)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = showFaceMesh,
                        onClick = onToggleFaceMesh,
                        label = {
                            Text(
                                text = if (showFaceMesh) "Mesh Overlay: ON" else "Mesh Overlay: OFF",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00E676).copy(alpha = 0.22f),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.06f),
                            labelColor = Color(0xFFB0BEC5)
                        )
                    )

                    FilterChip(
                        selected = activeDelegate == InferenceDelegate.GPU,
                        onClick = onToggleDelegate,
                        label = {
                            Text(
                                text = "Delegate: ${activeDelegate.name}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFEA00).copy(alpha = 0.22f),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.06f),
                            labelColor = Color(0xFFB0BEC5)
                        )
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ℹ️ $errorMessage",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color(0xFFFFEA00)
                    )
                }

                // Top 6 Real-Time Facial Blendshape Score Bars
                if (topBlendshapes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "TOP LIVE BLENDSHAPES (52 FACS UNITS)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color(0xFF90A4AE)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    topBlendshapes.forEach { metric ->
                        BlendshapeScoreRow(metric = metric)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetricPill(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                ),
                color = Color(0xFF90A4AE)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = valueColor
            )
        }
    }
}

@Composable
private fun BlendshapeScoreRow(metric: BlendshapeMetric) {
    val animatedScore by animateFloatAsState(
        targetValue = metric.score.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 120),
        label = "BlendshapeBar_${metric.name}"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = metric.name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            ),
            color = Color.White,
            modifier = Modifier.width(148.dp)
        )

        LinearProgressIndicator(
            progress = { animatedScore },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Color(0xFF80DEEA),
            trackColor = Color.White.copy(alpha = 0.10f),
            strokeCap = StrokeCap.Round
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "${(animatedScore * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            ),
            color = Color(0xFF80DEEA),
            modifier = Modifier.width(36.dp)
        )
    }
}
