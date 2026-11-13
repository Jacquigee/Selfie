package com.jacqui.selfie.ui.mapper

import androidx.compose.ui.geometry.Offset
import com.jacqui.selfie.domain.model.NormalizedPoint
import kotlin.math.max

/**
 * Platform-independent 2D pixel coordinate on the target UI surface.
 */
data class PixelPoint(
    val x: Float,
    val y: Float
) {
    /**
     * Converts this [PixelPoint] into a Jetpack Compose [Offset] for `Canvas` drawing.
     */
    fun toComposeOffset(): Offset = Offset(x = x, y = y)
}

/**
 * **Presentation Takeaway #4: Accurate Screen Transforms**
 *
 * Maps normalized `[0.0f .. 1.0f]` MediaPipe facial landmarks from the upright camera
 * analysis frame (`sourceWidth x sourceHeight`) onto a dynamic UI layout (`canvasWidth x canvasHeight`).
 *
 * ### Why Naive `normX * canvasWidth` Fails in Production:
 * 1. **Aspect Ratio Mismatch (Center-Crop / `FILL_CENTER`)**:
 *    Camera sensors typically capture in `4:3` (e.g., `480x640` in portrait), whereas modern
 *    phone screens have tall aspect ratios like `9:20` (e.g., `1080x2400`). When the camera
 *    viewfinder uses center-crop (`FILL_CENTER`) to fill the screen without black bars, the
 *    left and right edges of the camera frame are cropped off-screen. Multiplying `normX` by
 *    `canvasWidth` without accounting for the crop offset compresses the face mesh horizontally!
 * 2. **Front-Camera Mirroring**:
 *    Selfie previews are horizontally mirrored so moving your head right moves your image
 *    right on screen. However, the raw `ImageAnalysis` buffer is unmirrored. Therefore, when
 *    `isFrontCamera == true`, we must mirror the normalized X coordinate (`1.0f - normX`)
 *    before applying scale and crop translation.
 *
 * ### Mathematical Formula (`FILL_CENTER` / `ContentScale.Crop`):
 * ```
 * mirroredX   = if (isFrontCamera) (1f - normX) else normX
 * scaleFactor = max(canvasWidth / sourceWidth, canvasHeight / sourceHeight)
 * scaledW     = sourceWidth * scaleFactor
 * scaledH     = sourceHeight * scaleFactor
 * offsetX     = (canvasWidth - scaledW) / 2f
 * offsetY     = (canvasHeight - scaledH) / 2f
 *
 * screenX     = mirroredX * scaledW + offsetX
 * screenY     = normY * scaledH + offsetY
 * ```
 */
object CoordinateMapper {

    /**
     * Transforms a single normalized landmark coordinate `(normX, normY)` in `[0f..1f]`
     * into target pixel coordinates on a surface of size `(canvasWidth, canvasHeight)`.
     *
     * @param normX Normalized horizontal coordinate `[0f..1f]` in upright source image space.
     * @param normY Normalized vertical coordinate `[0f..1f]` in upright source image space.
     * @param sourceWidth Upright width of the analyzed camera frame in pixels.
     * @param sourceHeight Upright height of the analyzed camera frame in pixels.
     * @param canvasWidth Width of the target Compose `Canvas` in pixels.
     * @param canvasHeight Height of the target Compose `Canvas` in pixels.
     * @param isFrontCamera Whether horizontal selfie mirroring (`1f - normX`) should be applied.
     * @return [PixelPoint] in target `Canvas` coordinates.
     */
    fun mapNormalizedToPixel(
        normX: Float,
        normY: Float,
        sourceWidth: Int,
        sourceHeight: Int,
        canvasWidth: Float,
        canvasHeight: Float,
        isFrontCamera: Boolean
    ): PixelPoint {
        if (sourceWidth <= 0 || sourceHeight <= 0 || canvasWidth <= 0f || canvasHeight <= 0f) {
            return PixelPoint(0f, 0f)
        }

        // Step 1: Mirror X axis for front-facing camera so overlay matches the selfie preview
        val effectiveNormX = if (isFrontCamera) 1.0f - normX else normX

        // Step 2: Compute uniform FILL_CENTER (Center-Crop) scale factor
        val scaleX = canvasWidth / sourceWidth.toFloat()
        val scaleY = canvasHeight / sourceHeight.toFloat()
        val scaleFactor = max(scaleX, scaleY)

        // Step 3: Compute scaled image dimensions and centering offsets (negative along cropped axis)
        val scaledWidth = sourceWidth * scaleFactor
        val scaledHeight = sourceHeight * scaleFactor
        val offsetX = (canvasWidth - scaledWidth) * 0.5f
        val offsetY = (canvasHeight - scaledHeight) * 0.5f

        // Step 4: Project normalized point into scaled & centered canvas space
        val pixelX = effectiveNormX * scaledWidth + offsetX
        val pixelY = normY * scaledHeight + offsetY

        return PixelPoint(x = pixelX, y = pixelY)
    }

    /**
     * Convenience overload that maps a [NormalizedPoint] directly to a Compose [Offset].
     */
    fun mapToOffset(
        point: NormalizedPoint,
        sourceWidth: Int,
        sourceHeight: Int,
        canvasWidth: Float,
        canvasHeight: Float,
        isFrontCamera: Boolean
    ): Offset = mapNormalizedToPixel(
        normX = point.x,
        normY = point.y,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        isFrontCamera = isFrontCamera
    ).toComposeOffset()
}
