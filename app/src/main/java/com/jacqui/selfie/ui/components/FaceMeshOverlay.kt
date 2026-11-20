package com.jacqui.selfie.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jacqui.selfie.domain.model.Emotion
import com.jacqui.selfie.domain.model.NormalizedPoint
import com.jacqui.selfie.ui.mapper.CoordinateMapper

/**
 * **Presentation Takeaway #4: Coordinate Mapping & Overlays**
 *
 * Declarative Jetpack Compose [Canvas] overlay that projects MediaPipe's 478 normalized
 * facial landmarks onto the screen in real time using [CoordinateMapper].
 *
 * The contour lines and keypoint vertices dynamically animate their tint color to match
 * the currently classified [Emotion].
 */
@Composable
fun FaceMeshOverlay(
    landmarks: List<NormalizedPoint>,
    sourceWidth: Int,
    sourceHeight: Int,
    isFrontCamera: Boolean,
    dominantEmotion: Emotion,
    modifier: Modifier = Modifier
) {
    val targetColor = Color(dominantEmotion.colorHex)
    val animatedAccentColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 250),
        label = "MeshEmotionTint"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        if (landmarks.isEmpty() || sourceWidth <= 0 || sourceHeight <= 0) return@Canvas

        val canvasW = size.width
        val canvasH = size.height

        // Pre-map all 478 landmarks into Canvas pixel coordinates once per frame
        val mappedPoints: List<Offset> = landmarks.map { point ->
            CoordinateMapper.mapToOffset(
                point = point,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                canvasWidth = canvasW,
                canvasHeight = canvasH,
                isFrontCamera = isFrontCamera
            )
        }

        // 1. Draw subtle mesh points across the face
        val pointColor = animatedAccentColor.copy(alpha = 0.42f)
        for (i in mappedPoints.indices step 2) {
            drawCircle(
                color = pointColor,
                radius = 2.4f,
                center = mappedPoints[i]
            )
        }

        // 2. Draw primary anatomical contours (Face Oval, Eyes, Eyebrows, Lips, Nose Bridge)
        val contourStroke = Stroke(
            width = 3.8f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val glowStroke = Stroke(
            width = 8.5f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        val contours = listOf(
            FACE_OVAL_INDICES to true,
            LEFT_EYE_INDICES to true,
            RIGHT_EYE_INDICES to true,
            LEFT_EYEBROW_INDICES to false,
            RIGHT_EYEBROW_INDICES to false,
            OUTER_LIPS_INDICES to true,
            INNER_LIPS_INDICES to true,
            NOSE_BRIDGE_INDICES to false
        )

        for ((indices, closed) in contours) {
            drawLandmarkContour(
                mappedPoints = mappedPoints,
                indices = indices,
                closePath = closed,
                color = animatedAccentColor.copy(alpha = 0.22f),
                stroke = glowStroke
            )
            drawLandmarkContour(
                mappedPoints = mappedPoints,
                indices = indices,
                closePath = closed,
                color = animatedAccentColor.copy(alpha = 0.90f),
                stroke = contourStroke
            )
        }

        // 3. Highlight iris centers if refined landmarks (indices 468 & 473) are present
        if (mappedPoints.size > RIGHT_IRIS_CENTER_INDEX) {
            val leftIris = mappedPoints[LEFT_IRIS_CENTER_INDEX]
            val rightIris = mappedPoints[RIGHT_IRIS_CENTER_INDEX]
            drawCircle(color = Color.White, radius = 4.5f, center = leftIris)
            drawCircle(color = animatedAccentColor, radius = 8.5f, center = leftIris, style = Stroke(width = 2.5f))
            drawCircle(color = Color.White, radius = 4.5f, center = rightIris)
            drawCircle(color = animatedAccentColor, radius = 8.5f, center = rightIris, style = Stroke(width = 2.5f))
        }
    }
}

private fun DrawScope.drawLandmarkContour(
    mappedPoints: List<Offset>,
    indices: IntArray,
    closePath: Boolean,
    color: Color,
    stroke: Stroke
) {
    if (indices.isEmpty()) return
    val path = Path()
    var started = false

    for (index in indices) {
        if (index in mappedPoints.indices) {
            val pt = mappedPoints[index]
            if (!started) {
                path.moveTo(pt.x, pt.y)
                started = true
            } else {
                path.lineTo(pt.x, pt.y)
            }
        }
    }
    if (started && closePath) {
        path.close()
    }
    drawPath(path = path, color = color, style = stroke)
}

// Canonical MediaPipe 478 Face Mesh contour index loops
private val FACE_OVAL_INDICES = intArrayOf(
    10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
    397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
    172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109
)

private val LEFT_EYE_INDICES = intArrayOf(
    33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246
)

private val RIGHT_EYE_INDICES = intArrayOf(
    362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398
)

private val LEFT_EYEBROW_INDICES = intArrayOf(
    70, 63, 105, 66, 107, 55, 65, 52, 53, 46
)

private val RIGHT_EYEBROW_INDICES = intArrayOf(
    300, 293, 334, 296, 336, 285, 295, 282, 283, 276
)

private val OUTER_LIPS_INDICES = intArrayOf(
    61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291,
    409, 270, 269, 267, 0, 37, 39, 40, 185
)

private val INNER_LIPS_INDICES = intArrayOf(
    78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308,
    415, 310, 311, 312, 13, 82, 81, 80, 191
)

private val NOSE_BRIDGE_INDICES = intArrayOf(168, 6, 197, 195, 5, 4)

private const val LEFT_IRIS_CENTER_INDEX = 468
private const val RIGHT_IRIS_CENTER_INDEX = 473
