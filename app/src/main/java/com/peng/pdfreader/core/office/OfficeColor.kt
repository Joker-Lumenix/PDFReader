package com.peng.pdfreader.core.office

/**
 * Minimal RGBA colour value used across the Office parsers.
 *
 * The parsers are pure Kotlin and must not depend on any UI framework, so they
 * use this tiny value type instead of a framework colour class. Channels are
 * normalised to the 0..1 range.
 */
data class OfficeColor(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float = 1f,
) {
    /** Creates a colour from a 32 bit ARGB value. */
    constructor(value: Long) : this(
        red = ((value shr 16) and 0xFF) / 255f,
        green = ((value shr 8) and 0xFF) / 255f,
        blue = (value and 0xFF) / 255f,
        alpha = ((value shr 24) and 0xFF) / 255f,
    )

    fun copyAlpha(alpha: Float): OfficeColor = copy(alpha = alpha)

    companion object {
        val White = OfficeColor(1f, 1f, 1f, 1f)
        val Black = OfficeColor(0f, 0f, 0f, 1f)
        val Transparent = OfficeColor(0f, 0f, 0f, 0f)
    }
}
