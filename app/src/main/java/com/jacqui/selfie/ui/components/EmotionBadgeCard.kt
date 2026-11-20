package com.jacqui.selfie.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.jacqui.selfie.domain.model.Emotion
import kotlin.math.roundToInt

/**
 * Expressive floating card at the bottom of the viewfinder displaying the real-time
 * classified [Emotion], its confidence score, anatomical FACS drivers, and candidate chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmotionBadgeCard(
    dominantEmotion: Emotion,
    confidence: Float,
    emotionScores: Map<Emotion, Float>,
    faceDetected: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor by animateColorAsState(
        targetValue = if (faceDetected) Color(dominantEmotion.colorHex) else Color(0xFF90A4AE),
        animationSpec = tween(durationMillis = 250),
        label = "EmotionAccentColor"
    )

    val animatedConfidence by animateFloatAsState(
        targetValue = if (faceDetected) confidence.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "ConfidenceProgress"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = accentColor.copy(alpha = 0.55f),
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xD910141D),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Animated Emoji Avatar
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.18f))
                        .border(1.5.dp, accentColor.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = if (faceDetected) dominantEmotion.emoji else "🔍",
                        transitionSpec = {
                            (scaleIn(tween(200)) + fadeIn(tween(200))) togetherWith fadeOut(tween(150))
                        },
                        label = "EmotionEmoji"
                    ) { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 30.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (faceDetected) dominantEmotion.displayName.uppercase() else "SEARCHING FOR FACE…",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.1.sp
                            ),
                            color = Color.White
                        )

                        if (faceDetected) {
                            Text(
                                text = "${(animatedConfidence * 100).roundToInt()}%",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = accentColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (faceDetected) {
                            "FACS: ${dominantEmotion.facsSummary}"
                        } else {
                            "Position your face inside the camera viewfinder"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color(0xFFB0BEC5)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { animatedConfidence },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = accentColor,
                        trackColor = Color.White.copy(alpha = 0.12f),
                        strokeCap = StrokeCap.Round
                    )
                }
            }

            // Secondary emotion distribution pills when a face is active
            if (faceDetected && emotionScores.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Emotion.entries
                        .filter { it != Emotion.NEUTRAL }
                        .forEach { emotion ->
                            val score = emotionScores[emotion] ?: 0f
                            val isSelected = emotion == dominantEmotion
                            val chipColor = Color(emotion.colorHex)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) chipColor.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.06f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) chipColor else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${emotion.emoji} ${emotion.displayName} ${(score * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) Color.White else Color(0xFFB0BEC5)
                                )
                            }
                        }
                }
            }
        }
    }
}
