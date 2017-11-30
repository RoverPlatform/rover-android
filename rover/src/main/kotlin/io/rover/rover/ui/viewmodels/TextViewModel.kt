package io.rover.rover.ui.viewmodels

import android.graphics.Paint
import android.graphics.Typeface
import io.rover.rover.core.domain.FontWeight
import io.rover.rover.core.domain.TextAlignment
import io.rover.rover.core.domain.TextBlock
import io.rover.rover.ui.MeasurementService
import io.rover.rover.ui.types.Font
import io.rover.rover.ui.types.FontAppearance
import io.rover.rover.ui.types.RectF
import io.rover.rover.ui.views.asAndroidColor

/**
 * Text styling and size concerns.
 */
class TextViewModel(
    private val block: TextBlock,
    private val measurementService: MeasurementService
) : TextViewModelInterface {
    override val text: String
        get() = block.text

    override val fontAppearance: FontAppearance
        // this maps from the Rover font weight to a named font-family and typeface style,
        // which is what Android will ultimately expect since it doesn't explicitly support
        // a font weight.
        get() {
            val font = mapFontWeightToFont(block.textFont.weight)

            return FontAppearance(
                block.textFont.size,
                font,
                block.textColor.asAndroidColor(),
                when (block.textAlignment) {
                    TextAlignment.Center -> Paint.Align.CENTER
                    TextAlignment.Left -> Paint.Align.LEFT
                    TextAlignment.Right -> Paint.Align.RIGHT
                }
            )
        }

    override fun boldRelativeToBlockWeight(): Font {
        FontWeight.values().lastIndex

        val addedOrdinal = block.textFont.weight.ordinal + 3
        val addedWeight = if (addedOrdinal <= FontWeight.values().lastIndex) {
            FontWeight.values()[addedOrdinal]
        } else {
            FontWeight.values().last()
        }

        return mapFontWeightToFont(addedWeight)
    }

    private fun mapFontWeightToFont(fontWeight: FontWeight): Font {
        return when (fontWeight) {
            // Refer to Android's frameworks/base's data/fonts.xml.  We are basically reversing the
            // aliases and filling in the gaps where a font weight is not available at all
            // (typically by rounding down).  Note that the typeface style
            // (Typeface.NORMAL/Typeface.BOLD, etc.) causes Android to use one of two behaviours:
            // for bold or regular, select font weight 400 (regular) or 700 (bold).  For all others,
            // it does the standard behaviour of adding 300 to the font weight.  We never need to
            // avail ourselves of that second behaviour; aliases are available for all the other
            // font weights, so we only will use Typeface.BOLD for selecting base bold (700).

            // 100 is aliased to sans-serif-thin, which is more like a 200.  Rounding up here.
            FontWeight.UltraLight -> Font("sans-serif-thin", Typeface.NORMAL)

            // 200 is missing, but sans-serif-thin, the "100 weight" font is more like a 200 anyway.
            FontWeight.Thin -> Font("sans-serif-thin", Typeface.NORMAL)

            // 300 is aliased to sans-serif-light.
            FontWeight.Light -> Font("sans-serif-light", Typeface.NORMAL)

            // 400 is the default weight for sans-serif, no alias needed.
            FontWeight.Regular -> Font("sans-serif", Typeface.NORMAL)

            // 500 is aliased to sans-serif-medium.
            FontWeight.Medium -> Font("sans-serif-medium", Typeface.NORMAL)

            // 600 is missing.  We'll round it down to 500.
            FontWeight.SemiBold -> Font("sans-serif-medium", Typeface.NORMAL)

            // 700 is standard bold, no alias needed.
            FontWeight.Bold -> Font("sans-serif", Typeface.BOLD)

            // 800 is missing.  We'll round down to standard bold.
            FontWeight.Heavy -> Font("sans-serif", Typeface.BOLD)

            // 900 is aliased to sans-serif-black.
            FontWeight.Black -> Font("sans-serif-black", Typeface.NORMAL)
        }
    }

    override fun intrinsicHeight(bounds: RectF): Float {
        return measurementService.measureHeightNeededForRichText(
            block.text,
            fontAppearance,
            boldRelativeToBlockWeight(),
            bounds.width()
        )
    }
}