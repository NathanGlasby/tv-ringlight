package com.ringlight.tv

/** Settings shared by the editor, overlay, and remote shortcuts. No Android dependency. */
object RingLightSettings {
    val COLORS = listOf(
        0xFFFFFFFF.toInt(), 0xFFFFD27F.toInt(), 0xFFFFFF4D.toInt(), 0xFFFF4D4D.toInt(),
        0xFF4DFF91.toInt(), 0xFF4DB8FF.toInt(), 0xFFB84DFF.toInt(), 0xFFFF4DC8.toInt(),
    )
    val COLOR_NAMES = listOf("White", "Warm white", "Yellow", "Red", "Green", "Blue", "Purple", "Pink")
    val INTENSITY_LEVELS = listOf(0.25f, 0.50f, 0.75f, 1f)
    val INTENSITY_LABELS = listOf("25%", "50%", "75%", "100%")
    val DURATION_STEPS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 360, 480)

    const val DEFAULT_COLOR_INDEX = 0
    const val DEFAULT_INTENSITY_INDEX = 3
    const val DEFAULT_THICKNESS_PCT = 15
    const val DEFAULT_AUTO_OFF_ENABLED = true
    const val DEFAULT_AUTO_OFF_MINUTES = 180
    const val MIN_THICKNESS_PCT = 5
    const val MAX_THICKNESS_PCT = 40
    const val THICKNESS_STEP = 5
    const val MIN_AUTO_OFF_MINUTES = 5
    const val MAX_AUTO_OFF_MINUTES = 480

    fun colorIndex(value: Int): Int = value.coerceIn(COLORS.indices)
    fun intensityIndex(value: Int): Int = value.coerceIn(INTENSITY_LEVELS.indices)
    fun thicknessPct(value: Int): Int = value.coerceIn(MIN_THICKNESS_PCT, MAX_THICKNESS_PCT)
    fun autoOffMinutes(value: Int): Int = value.coerceIn(MIN_AUTO_OFF_MINUTES, MAX_AUTO_OFF_MINUTES)

    fun previousDuration(current: Int): Int =
        DURATION_STEPS.lastOrNull { it < current } ?: DURATION_STEPS.first()

    fun nextDuration(current: Int): Int =
        DURATION_STEPS.firstOrNull { it > current } ?: DURATION_STEPS.last()
}
