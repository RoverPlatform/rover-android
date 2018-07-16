package io.rover.experiences.ui.blocks.concerns.text

import android.graphics.Paint

/**
 * A selected [Font] with a size, colour, and alignment to be drawn.
 */
data class FontAppearance(
    /**
     * Font size, in Android Scalable Pixels.
     */
    val fontSize: Int,

    val font: Font,

    /**
     * An ARGB color value suitable for use with various Android APIs.
     */
    val color: Int,

    val align: Paint.Align
)