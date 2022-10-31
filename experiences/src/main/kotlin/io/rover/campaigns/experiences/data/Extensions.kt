package io.rover.campaigns.experiences.data

import android.graphics.Paint
import android.graphics.Typeface
import io.rover.campaigns.experiences.data.domain.Color
import io.rover.campaigns.experiences.data.domain.Font
import io.rover.campaigns.experiences.data.domain.FontWeight
import io.rover.campaigns.experiences.data.domain.TextAlignment
import io.rover.campaigns.experiences.ui.asAndroidColor
import io.rover.campaigns.experiences.ui.blocks.concerns.text.FontAppearance

internal fun TextAlignment.toPaintAlign(): Paint.Align {
    return when (this) {
        TextAlignment.Center -> Paint.Align.CENTER
        TextAlignment.Left -> Paint.Align.LEFT
        TextAlignment.Right -> Paint.Align.RIGHT
    }
}

internal fun Font.getFontAppearance(color: Color, alignment: TextAlignment): FontAppearance {
    val font = this.weight.mapToFont()

    return FontAppearance(this.size, font, color.asAndroidColor(), alignment.toPaintAlign())
}

internal fun FontWeight.mapToFont(): io.rover.campaigns.experiences.ui.blocks.concerns.text.Font {
    return when (this) {
        // Refer to Android's frameworks/base's data/fonts.xml.  We are basically reversing the
        // aliases and filling in the gaps where a font weight is not available at all
        // (typically by rounding down).  Note that the typeface style
        // (Typeface.NORMAL/Typeface.BOLD, etc.) causes Android to use one of two behaviours:
        // for bold or regular, select font weight 400 (regular) or 700 (bold).  For all others,
        // it does the standard behaviour of adding 300 to the font weight.  We never need to
        // avail ourselves of that second behaviour; aliases are available for all the other
        // font weights, so we only will use Typeface.BOLD for selecting base bold (700).

        // 100 is aliased to sans-serif-thin, which is more like a 200.  Rounding up here.
        FontWeight.UltraLight -> io.rover.campaigns.experiences.ui.blocks.concerns.text.Font(
            "sans-serif-thin",
            Typeface.NORMAL
        )

        // 200 is missing, but sans-serif-thin, the "100 weight" font is more like a 200 anyway.
        FontWeight.Thin -> io.rover.campaigns.experiences.ui.blocks.concerns.text.Font(
            "sans-serif-thin",
            Typeface.NORMAL
        )

        // 300 is aliased to sans-serif-light.
        FontWeight.Light -> io.rover.campaigns.experiences.ui.blocks.concerns.text.Font(
            "sans-serif-light",
            Typeface.NORMAL
        )

        // 400 is the default weight for sans-serif, no alias needed.
        FontWeight.Regular -> io.rover.campaigns.experiences.ui.blocks.concerns.text.Font(
            "sans-serif",
            Typeface.NORMAL
        )

        // 500 is aliased to sans-serif-medium.
        FontWeight.Medium -> io.rover.campaigns.experiences.ui.blocks.concerns.text.Font(
            "sans-serif-medium",
            Typeface.NORMAL
        )

        // 600 is missing.  We'll round it down to 500.
        FontWeight.SemiBold -> io.rover.campaigns.experiences.ui.blocks.concerns.text.Font(
            "sans-serif-medium",
            Typeface.NORMAL
        )

        // 700 is standard bold, no alias needed.
        FontWeight.Bold -> io.rover.campaigns.experiences.ui.blocks.concerns.text.Font("sans-serif", Typeface.BOLD)

        // 800 is missing.  We'll round down to standard bold.
        FontWeight.Heavy -> io.rover.campaigns.experiences.ui.blocks.concerns.text.Font("sans-serif", Typeface.BOLD)

        // 900 is aliased to sans-serif-black.
        FontWeight.Black -> io.rover.campaigns.experiences.ui.blocks.concerns.text.Font(
            "sans-serif-black",
            Typeface.NORMAL
        )
    }
}