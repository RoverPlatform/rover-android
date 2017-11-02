package io.rover.rover.ui.viewmodels

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import io.rover.rover.core.domain.FontWeight
import io.rover.rover.core.domain.TextAlignment
import io.rover.rover.core.domain.TextBlock
import io.rover.rover.core.logging.log
import io.rover.rover.ui.MeasurementService
import io.rover.rover.ui.types.ViewType
import io.rover.rover.ui.views.asAndroidColor

class TextBlockViewModel(
    private val block: TextBlock,
    private val measurementService: MeasurementService
): TextBlockViewModelInterface,
    BlockViewModel(block),
    BackgroundViewModelInterface by BackgroundViewModel(block),
    BorderViewModelInterface by BorderViewModel(block) {
    override val viewType: ViewType = ViewType.Text

    override fun intrinsicHeight(bounds: RectF): Float {
        return measurementService.measureHeightNeededForRichText(
            block.text,
            fontFace,
            bounds.width()
        )
    }

    override val text: String
        get() = block.text

    // TODO: we need to transform a Spanned.  However, that requires an Android dependency,
    // which breaks our tests, and in this case using our own types isn't really an option here....
    // I guess I can put it in the view, and acknowledge that it cannot be used in tests (at the
    // very least though, I can put the font-face step-up logic here in the view-model and test
    // that).
    // Perhaps set a TypefaceSpan? However, because we're setting not just family but also style,
    // it means we will probably have to create a custom version of TypefaceSpan.  Btw most of the
    //

    override val fontFace: FontFace
        // this maps from the Rover font weight to a named font-family and typeface style,
        // which is what Android will ultimately expect since it doesn't explicitly support
        // a font weight.
        get() {
            val font = mapFontWeightToFont(block.textFont.weight)

            return FontFace(
                block.textFont.size,
                font,
                block.textColor.asAndroidColor(),
                when(block.textAlignment) {
                    TextAlignment.Center -> Paint.Align.CENTER
                    TextAlignment.Left -> Paint.Align.LEFT
                    TextAlignment.Right -> Paint.Align.RIGHT
                }
            )
        }

    override fun boldRelativeToBlockWeight(): Font {
        FontWeight.values().lastIndex

        val addedOrdinal = block.textFont.weight.ordinal + 3
        val addedWeight = if(addedOrdinal <= FontWeight.values().lastIndex) {
            FontWeight.values()[addedOrdinal]
        } else {
            FontWeight.values().last()
        }

        log.d("Mapped ${block.textFont.weight} to $addedWeight")

        return mapFontWeightToFont(addedWeight)
    }

    private fun mapFontWeightToFont(fontWeight: FontWeight): Font {
        return when(fontWeight) {

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


//            FontWeight.UltraLight -> Font("sans-serif-thin", normal)
//            FontWeight.Thin -> Font("sans-serif-thin", normal)
//            FontWeight.Light -> Font("sans-serif-light", normal)
//            FontWeight.Regular -> Font("sans-serif", normal)
//            FontWeight.Medium -> Font("sans-serif-medium",normal)
//            FontWeight.SemiBold -> Font("sans-serif-medium", bold)
//            FontWeight.Bold -> Font("sans-serif-bold", Typeface.NORMAL)
//            FontWeight.Heavy -> Font("sans-serif", normal)
//            FontWeight.Black -> Font("sans-serif-black", normal)


        }
    }
}

