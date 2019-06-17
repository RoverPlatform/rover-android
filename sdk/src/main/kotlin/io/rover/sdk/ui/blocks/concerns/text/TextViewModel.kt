package io.rover.sdk.ui.blocks.concerns.text

import android.graphics.Paint
import android.graphics.Typeface
import io.rover.sdk.services.MeasurementService
import io.rover.sdk.data.domain.FontWeight
import io.rover.sdk.data.domain.Text
import io.rover.sdk.data.domain.TextAlignment
import io.rover.sdk.data.mapToFont
import io.rover.sdk.logging.log
import io.rover.sdk.ui.RectF
import io.rover.sdk.ui.asAndroidColor

/**
 * Text styling and size concerns.
 */
internal class TextViewModel(
    private val styledText: Text,
    private val measurementService: MeasurementService,
    override val singleLine: Boolean = false,
    override val centerVertically: Boolean = false
) : TextViewModelInterface {
    override val text: String
        get() = styledText.rawValue

    override val fontAppearance: FontAppearance
        // this maps from the Rover font weight to a named font-family and typeface style,
        // which is what Android will ultimately expect since it doesn't explicitly support
        // a font weight.
        get() {
            val font = styledText.font.weight.mapToFont()

            return FontAppearance(
                styledText.font.size,
                font,
                styledText.color.asAndroidColor(),
                when (styledText.alignment) {
                    TextAlignment.Center -> Paint.Align.CENTER
                    TextAlignment.Left -> Paint.Align.LEFT
                    TextAlignment.Right -> Paint.Align.RIGHT
                }
            )
        }

    override fun boldRelativeToBlockWeight(): Font {
        FontWeight.values().lastIndex

        val addedOrdinal = styledText.font.weight.ordinal + 3
        val addedWeight = if (addedOrdinal <= FontWeight.values().lastIndex) {
            FontWeight.values()[addedOrdinal]
        } else {
            FontWeight.values().last()
        }

        return addedWeight.mapToFont()
    }

    override fun intrinsicHeight(bounds: RectF): Float {
        val width = if (bounds.width() < 0) {
            log.w("Bounds width somehow less than zero? Was ${bounds.width()}. Full bounds is $bounds. Bottoming out at zero.  Can happen if Fill block shrank down to zero on narrower screen than expected.")
            0f
        } else bounds.width()
        return measurementService.measureHeightNeededForRichText(
            if (singleLine) {
                // only measure a single line as configured.
                // However, as things stand, no single-line TextViewModels are actually measured, so
                // this case for intrinsicHeight() is only here for completeness.
                "1"
            } else {
                styledText.rawValue
            },
            fontAppearance,
            boldRelativeToBlockWeight(),
            width
        )
    }
}