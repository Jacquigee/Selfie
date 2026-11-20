package com.jacqui.selfie.ui.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CoordinateMapper] verifying `FILL_CENTER` (`ContentScale.Crop`) scaling,
 * aspect-ratio crop offsets, and front-camera horizontal mirroring.
 */
class CoordinateMapperTest {

    @Test
    fun `center landmark (0_5, 0_5) maps to exact canvas center regardless of aspect ratio or mirroring`() {
        val frontResult = CoordinateMapper.mapNormalizedToPixel(
            normX = 0.5f,
            normY = 0.5f,
            sourceWidth = 480,
            sourceHeight = 640,
            canvasWidth = 1080f,
            canvasHeight = 2400f,
            isFrontCamera = true
        )
        assertEquals(540f, frontResult.x, 0.01f)
        assertEquals(1200f, frontResult.y, 0.01f)

        val backResult = CoordinateMapper.mapNormalizedToPixel(
            normX = 0.5f,
            normY = 0.5f,
            sourceWidth = 480,
            sourceHeight = 640,
            canvasWidth = 1080f,
            canvasHeight = 2400f,
            isFrontCamera = false
        )
        assertEquals(540f, backResult.x, 0.01f)
        assertEquals(1200f, backResult.y, 0.01f)
    }

    @Test
    fun `front camera horizontally mirrors X coordinates around canvas center`() {
        val leftEyeFront = CoordinateMapper.mapNormalizedToPixel(
            normX = 0.25f,
            normY = 0.40f,
            sourceWidth = 480,
            sourceHeight = 640,
            canvasWidth = 480f,
            canvasHeight = 640f,
            isFrontCamera = true
        )
        val leftEyeBack = CoordinateMapper.mapNormalizedToPixel(
            normX = 0.25f,
            normY = 0.40f,
            sourceWidth = 480,
            sourceHeight = 640,
            canvasWidth = 480f,
            canvasHeight = 640f,
            isFrontCamera = false
        )

        // When unmirrored (back camera): 0.25 * 480 = 120px
        assertEquals(120f, leftEyeBack.x, 0.01f)
        // When mirrored (front camera): (1 - 0.25) * 480 = 360px
        assertEquals(360f, leftEyeFront.x, 0.01f)
        // Y coordinate is unchanged by horizontal mirroring
        assertEquals(leftEyeBack.y, leftEyeFront.y, 0.01f)
    }

    @Test
    fun `tall phone screen crops left and right edges of 3_4 camera frame using FILL_CENTER`() {
        // Source: 480x640 (aspect ratio 0.75)
        // Target Canvas: 1080x2400 (aspect ratio 0.45)
        // scaleX = 1080 / 480 = 2.25, scaleY = 2400 / 640 = 3.75 -> scaleFactor = 3.75
        // scaledWidth = 480 * 3.75 = 1800, offsetX = (1080 - 1800) / 2 = -360
        val leftEdgeBack = CoordinateMapper.mapNormalizedToPixel(
            normX = 0.0f,
            normY = 0.0f,
            sourceWidth = 480,
            sourceHeight = 640,
            canvasWidth = 1080f,
            canvasHeight = 2400f,
            isFrontCamera = false
        )

        assertEquals(-360f, leftEdgeBack.x, 0.01f)
        assertEquals(0f, leftEdgeBack.y, 0.01f)
        assertTrue("Left sensor edge should be cropped off-screen (< 0)", leftEdgeBack.x < 0f)
    }
}
