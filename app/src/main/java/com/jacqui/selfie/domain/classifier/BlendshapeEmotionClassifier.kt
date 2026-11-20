package com.jacqui.selfie.domain.classifier

import com.jacqui.selfie.domain.model.Emotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Result of classifying a single frame's 52 facial blendshape scores.
 *
 * @property dominantEmotion The winning discrete [Emotion] after temporal smoothing.
 * @property confidence Normalized confidence score in `[0.0f .. 1.0f]`.
 * @property allScores Smoothed score distribution across all non-neutral emotions.
 */
data class EmotionClassification(
    val dominantEmotion: Emotion,
    val confidence: Float,
    val allScores: Map<Emotion, Float>
)

/**
 * **Presentation Takeaway #3: On-Device Blendshape Emotion Logic**
 *
 * Pure Kotlin domain classifier that translates MediaPipe's 52 ARKit-compatible facial
 * blendshape scores into discrete [Emotion] states in real time.
 *
 * ### Why Blendshapes Instead of Raw Landmark Euclidean Distances?
 * Raw 2D/3D landmark distances change with camera distance, head pitch/yaw, and individual
 * facial proportions. MediaPipe's blendshape sub-network predicts normalized `[0..1]`
 * Facial Action Coding System (FACS) muscle activations that are invariant to scale and
 * head pose—making emotion classification far more reliable across diverse faces.
 *
 * ### Temporal Smoothing (Exponential Moving Average)
 * Raw per-frame ML predictions can oscillate when an expression sits near a decision
 * boundary. We apply an Exponential Moving Average (EMA):
 * ```
 * smoothed[t] = alpha * raw[t] + (1 - alpha) * smoothed[t - 1]
 * ```
 * coupled with an activation threshold ([activationThreshold]) so the UI badge transitions
 * fluidly without flickering during a live stage demo.
 *
 * @param smoothingAlpha EMA responsiveness weight in `(0.0f..1.0f]`. Default `0.38f` balances
 *   snappy responsiveness (~100ms) with strong jitter suppression.
 * @param activationThreshold Minimum composite score required to transition out of [Emotion.NEUTRAL].
 */
class BlendshapeEmotionClassifier(
    private val smoothingAlpha: Float = DEFAULT_SMOOTHING_ALPHA,
    private val activationThreshold: Float = DEFAULT_ACTIVATION_THRESHOLD
) {

    /**
     * Stores the previous frame's smoothed score for each [Emotion] to compute the EMA.
     * Guarded by `@Synchronized` in [classify] and [reset] for thread safety.
     */
    private val smoothedScores = mutableMapOf<Emotion, Float>()

    /**
     * Classifies a map of blendshape category names (`String`) to their `[0f..1f]` scores.
     *
     * @param blendshapes Map of MediaPipe blendshape names to scores.
     * @return [EmotionClassification] containing the dominant [Emotion], confidence, and full breakdown.
     */
    @Synchronized
    fun classify(blendshapes: Map<String, Float>): EmotionClassification {
        if (blendshapes.isEmpty()) {
            reset()
            return EmotionClassification(
                dominantEmotion = Emotion.NEUTRAL,
                confidence = 1.0f,
                allScores = Emotion.entries.associateWith { if (it == Emotion.NEUTRAL) 1f else 0f }
            )
        }

        // Helper to safely look up a blendshape score in [0f..1f]
        fun score(key: String): Float = (blendshapes[key] ?: 0f).coerceIn(0f, 1f)

        // Helper for bilateral (left + right) symmetrical facial muscle pairs
        fun bilateralAvg(leftKey: String, rightKey: String): Float =
            (score(leftKey) + score(rightKey)) * 0.5f

        // 1. HAPPY (AU12 Lip Corner Puller + AU6 Cheek Raiser)
        val smileAvg = bilateralAvg("mouthSmileLeft", "mouthSmileRight")
        val cheekSquintAvg = bilateralAvg("cheekSquintLeft", "cheekSquintRight")
        val rawHappy = clamp01(smileAvg * 0.80f + cheekSquintAvg * 0.30f)

        // 2. SURPRISED (AU26 Jaw Drop + AU5 Upper Lid Raiser + AU1/AU2 Brow Raiser)
        val jawOpen = score("jawOpen")
        val eyeWideAvg = bilateralAvg("eyeWideLeft", "eyeWideRight")
        val browInnerUp = score("browInnerUp")
        val browOuterUpAvg = bilateralAvg("browOuterUpLeft", "browOuterUpRight")
        val rawSurprised = clamp01(
            jawOpen * 0.52f +
                eyeWideAvg * 0.28f +
                max(browInnerUp, browOuterUpAvg) * 0.30f
        )

        // 3. ANGRY (AU4 Brow Lowerer + AU23 Lip Tightener / Press + AU9 Nose Wrinkler)
        val browDownAvg = bilateralAvg("browDownLeft", "browDownRight")
        val mouthPressAvg = bilateralAvg("mouthPressLeft", "mouthPressRight")
        val mouthShrugUpper = score("mouthShrugUpper")
        val eyeSquintAvg = bilateralAvg("eyeSquintLeft", "eyeSquintRight")
        // Suppress anger score if the user is clearly smiling or opening jaw in surprise
        val angerPenalty = max(smileAvg, jawOpen) * 0.5f
        val rawAngry = clamp01(
            browDownAvg * 0.65f +
                mouthPressAvg * 0.25f +
                eyeSquintAvg * 0.20f +
                mouthShrugUpper * 0.15f -
                angerPenalty
        )

        // 4. SAD (AU15 Lip Corner Depressor + AU1 Inner Brow Raiser + AU17 Chin Raiser)
        val mouthFrownAvg = bilateralAvg("mouthFrownLeft", "mouthFrownRight")
        val mouthShrugLower = score("mouthShrugLower")
        val mouthPucker = score("mouthPucker")
        val rawSad = clamp01(
            mouthFrownAvg * 0.55f +
                browInnerUp * 0.35f +
                mouthShrugLower * 0.25f +
                mouthPucker * 0.10f -
                smileAvg * 0.60f -
                jawOpen * 0.40f
        )

        // 5. DISGUSTED (AU9 Nose Wrinkler + AU10 Upper Lip Raiser)
        val noseSneerAvg = bilateralAvg("noseSneerLeft", "noseSneerRight")
        val mouthUpperUpAvg = bilateralAvg("mouthUpperUpLeft", "mouthUpperUpRight")
        val rawDisgusted = clamp01(
            noseSneerAvg * 0.65f +
                mouthUpperUpAvg * 0.40f +
                mouthShrugLower * 0.15f -
                smileAvg * 0.45f
        )

        // 6. WINK (Deliberate asymmetric eye closure: |eyeBlinkLeft - eyeBlinkRight|)
        val blinkLeft = score("eyeBlinkLeft")
        val blinkRight = score("eyeBlinkRight")
        val blinkDiff = abs(blinkLeft - blinkRight)
        val maxBlink = max(blinkLeft, blinkRight)
        val minBlink = min(blinkLeft, blinkRight)
        // High score only when one eye is strongly closed (>0.55) while the other remains open (<0.30)
        val rawWink = if (maxBlink > 0.50f && minBlink < 0.32f) {
            clamp01(blinkDiff * 1.15f)
        } else {
            clamp01(blinkDiff * 0.50f)
        }

        val rawScores = mapOf(
            Emotion.HAPPY to rawHappy,
            Emotion.SURPRISED to rawSurprised,
            Emotion.ANGRY to rawAngry,
            Emotion.SAD to rawSad,
            Emotion.DISGUSTED to rawDisgusted,
            Emotion.WINK to rawWink
        )

        // Apply Exponential Moving Average (EMA) smoothing per emotion
        val currentSmoothed = mutableMapOf<Emotion, Float>()
        for ((emotion, rawScore) in rawScores) {
            val previous = smoothedScores[emotion] ?: rawScore
            val updated = smoothingAlpha * rawScore + (1f - smoothingAlpha) * previous
            smoothedScores[emotion] = updated
            currentSmoothed[emotion] = updated
        }

        // Find the strongest expressive emotion
        val bestExpressive = currentSmoothed.maxByOrNull { it.value }
        val peakScore = bestExpressive?.value ?: 0f

        // Compute Neutral score inversely proportional to the strongest active expression
        val neutralScore = clamp01(1.0f - (peakScore / activationThreshold).coerceAtMost(1.0f) * 0.75f)
        currentSmoothed[Emotion.NEUTRAL] = neutralScore

        return if (bestExpressive != null && bestExpressive.value >= activationThreshold) {
            // Normalize confidence so threshold maps smoothly toward 1.0f
            val normalizedConfidence = clamp01(
                0.50f + ((bestExpressive.value - activationThreshold) / (1.0f - activationThreshold)) * 0.50f
            )
            EmotionClassification(
                dominantEmotion = bestExpressive.key,
                confidence = max(bestExpressive.value, normalizedConfidence),
                allScores = currentSmoothed.toMap()
            )
        } else {
            EmotionClassification(
                dominantEmotion = Emotion.NEUTRAL,
                confidence = clamp01(1.0f - peakScore),
                allScores = currentSmoothed.toMap()
            )
        }
    }

    /**
     * Resets the internal EMA smoothing history (e.g., when no face is in frame or camera flips).
     */
    @Synchronized
    fun reset() {
        smoothedScores.clear()
    }

    private fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)

    companion object {
        const val DEFAULT_SMOOTHING_ALPHA = 0.38f
        const val DEFAULT_ACTIVATION_THRESHOLD = 0.28f
    }
}
