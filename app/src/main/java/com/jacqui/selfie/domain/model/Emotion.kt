package com.jacqui.selfie.domain.model

/**
 * Discrete emotion states classified on-device from MediaPipe's 52 ARKit-compatible
 * facial blendshape scores.
 *
 * Each [Emotion] carries presentation metadata used by the UI overlay and Speaker HUD:
 * - [displayName]: Human-readable label shown on the emotion badge.
 * - [emoji]: Expressive emoji representation.
 * - [facsSummary]: Anatomical Facial Action Coding System (FACS) blendshape drivers,
 *   ideal for live demonstration during the presentation.
 * - [colorHex]: ARGB hex color token used to tint the face mesh and confidence bars.
 *
 * @see com.jacqui.selfie.domain.classifier.BlendshapeEmotionClassifier
 */
enum class Emotion(
    val displayName: String,
    val emoji: String,
    val facsSummary: String,
    val colorHex: Long
) {
    HAPPY(
        displayName = "Happy",
        emoji = "😄",
        facsSummary = "mouthSmileLeft/Right + cheekSquint",
        colorHex = 0xFF00E676L // Vibrant Emerald Green
    ),
    SURPRISED(
        displayName = "Surprised",
        emoji = "😲",
        facsSummary = "jawOpen + eyeWideLeft/Right + browInnerUp",
        colorHex = 0xFFFFEA00L // Electric Amber/Yellow
    ),
    ANGRY(
        displayName = "Angry",
        emoji = "😠",
        facsSummary = "browDownLeft/Right + mouthPress + noseSneer",
        colorHex = 0xFFFF5252L // Coral Red
    ),
    SAD(
        displayName = "Sad",
        emoji = "😢",
        facsSummary = "mouthFrownLeft/Right + browInnerUp",
        colorHex = 0xFF448AFFL // Ocean Blue
    ),
    DISGUSTED(
        displayName = "Disgusted",
        emoji = "🤢",
        facsSummary = "noseSneerLeft/Right + mouthUpperUp",
        colorHex = 0xFFB388FFL // Soft Violet
    ),
    WINK(
        displayName = "Wink",
        emoji = "😉",
        facsSummary = "|eyeBlinkLeft - eyeBlinkRight| + cheekSquint",
        colorHex = 0xFFFF80ABL // Playful Pink
    ),
    NEUTRAL(
        displayName = "Neutral",
        emoji = "😐",
        facsSummary = "Baseline relaxed facial musculature",
        colorHex = 0xFF80DEEAL // Calm Cyan
    )
}
