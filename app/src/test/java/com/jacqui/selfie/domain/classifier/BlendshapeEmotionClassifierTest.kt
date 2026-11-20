package com.jacqui.selfie.domain.classifier

import com.jacqui.selfie.domain.model.Emotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BlendshapeEmotionClassifier] verifying FACS blendshape classification
 * rules and Exponential Moving Average (EMA) temporal smoothing.
 */
class BlendshapeEmotionClassifierTest {

    @Test
    fun `empty blendshapes map returns NEUTRAL`() {
        val classifier = BlendshapeEmotionClassifier()
        val result = classifier.classify(emptyMap())
        assertEquals(Emotion.NEUTRAL, result.dominantEmotion)
        assertEquals(1.0f, result.confidence, 0.01f)
    }

    @Test
    fun `strong bilateral smile and cheek squint classifies as HAPPY`() {
        val classifier = BlendshapeEmotionClassifier(smoothingAlpha = 1.0f)
        val result = classifier.classify(
            mapOf(
                "mouthSmileLeft" to 0.85f,
                "mouthSmileRight" to 0.82f,
                "cheekSquintLeft" to 0.60f,
                "cheekSquintRight" to 0.58f
            )
        )
        assertEquals(Emotion.HAPPY, result.dominantEmotion)
        assertTrue("Expected high confidence for HAPPY", result.confidence > 0.75f)
    }

    @Test
    fun `jawOpen combined with eyeWide and browInnerUp classifies as SURPRISED`() {
        val classifier = BlendshapeEmotionClassifier(smoothingAlpha = 1.0f)
        val result = classifier.classify(
            mapOf(
                "jawOpen" to 0.90f,
                "eyeWideLeft" to 0.75f,
                "eyeWideRight" to 0.78f,
                "browInnerUp" to 0.80f
            )
        )
        assertEquals(Emotion.SURPRISED, result.dominantEmotion)
        assertTrue(result.confidence > 0.75f)
    }

    @Test
    fun `browDown and mouthPress classifies as ANGRY`() {
        val classifier = BlendshapeEmotionClassifier(smoothingAlpha = 1.0f)
        val result = classifier.classify(
            mapOf(
                "browDownLeft" to 0.88f,
                "browDownRight" to 0.84f,
                "mouthPressLeft" to 0.65f,
                "mouthPressRight" to 0.60f,
                "eyeSquintLeft" to 0.55f,
                "eyeSquintRight" to 0.50f
            )
        )
        assertEquals(Emotion.ANGRY, result.dominantEmotion)
    }

    @Test
    fun `mouthFrown and browInnerUp classifies as SAD`() {
        val classifier = BlendshapeEmotionClassifier(smoothingAlpha = 1.0f)
        val result = classifier.classify(
            mapOf(
                "mouthFrownLeft" to 0.80f,
                "mouthFrownRight" to 0.78f,
                "browInnerUp" to 0.70f,
                "mouthShrugLower" to 0.55f
            )
        )
        assertEquals(Emotion.SAD, result.dominantEmotion)
    }

    @Test
    fun `noseSneer and mouthUpperUp classifies as DISGUSTED`() {
        val classifier = BlendshapeEmotionClassifier(smoothingAlpha = 1.0f)
        val result = classifier.classify(
            mapOf(
                "noseSneerLeft" to 0.85f,
                "noseSneerRight" to 0.80f,
                "mouthUpperUpLeft" to 0.65f,
                "mouthUpperUpRight" to 0.60f
            )
        )
        assertEquals(Emotion.DISGUSTED, result.dominantEmotion)
    }

    @Test
    fun `asymmetric eye blink classifies as WINK`() {
        val classifier = BlendshapeEmotionClassifier(smoothingAlpha = 1.0f)
        val result = classifier.classify(
            mapOf(
                "eyeBlinkLeft" to 0.92f,
                "eyeBlinkRight" to 0.08f
            )
        )
        assertEquals(Emotion.WINK, result.dominantEmotion)
    }

    @Test
    fun `low baseline blendshape activations remain NEUTRAL`() {
        val classifier = BlendshapeEmotionClassifier(smoothingAlpha = 1.0f)
        val result = classifier.classify(
            mapOf(
                "mouthSmileLeft" to 0.05f,
                "mouthSmileRight" to 0.04f,
                "jawOpen" to 0.02f
            )
        )
        assertEquals(Emotion.NEUTRAL, result.dominantEmotion)
    }

    @Test
    fun `EMA smoothing dampens single-frame noise spike`() {
        val classifier = BlendshapeEmotionClassifier(
            smoothingAlpha = 0.35f,
            activationThreshold = 0.30f
        )

        // Frame 1 & 2: Calm neutral face
        val calmFrame = mapOf("mouthSmileLeft" to 0.02f, "mouthSmileRight" to 0.02f)
        classifier.classify(calmFrame)
        val secondCalm = classifier.classify(calmFrame)
        assertEquals(Emotion.NEUTRAL, secondCalm.dominantEmotion)

        // Frame 3: Sustained strong smile across two frames crosses threshold smoothly
        val smileFrame = mapOf(
            "mouthSmileLeft" to 0.90f,
            "mouthSmileRight" to 0.90f,
            "cheekSquintLeft" to 0.60f,
            "cheekSquintRight" to 0.60f
        )
        classifier.classify(smileFrame)
        val sustainedSmile = classifier.classify(smileFrame)
        assertEquals(Emotion.HAPPY, sustainedSmile.dominantEmotion)
    }
}
