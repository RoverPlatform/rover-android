package io.rover.rover.ui.viewmodels

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import io.rover.rover.core.domain.FontWeight
import io.rover.rover.core.domain.TextAlignment
import io.rover.rover.core.domain.TextBlock
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

    override val fontFace: FontFace
        // this maps from the Rover font format to what Android will ultimately expect
        get() {
            val (familyName, typefaceStyle) = when(block.textFont.weight) {
                FontWeight.UltraLight -> Pair("sans-serif-thin", Typeface.NORMAL)
                FontWeight.Thin -> Pair("sans-serif-thin", Typeface.NORMAL)
                FontWeight.Light -> Pair("sans-serif-light", Typeface.NORMAL)
                FontWeight.Regular -> Pair("sans-serif", Typeface.NORMAL)
                FontWeight.Medium -> Pair("sans-serif-medium", Typeface.NORMAL)
                FontWeight.SemiBold -> Pair("sans-serif-medium", Typeface.BOLD)
                FontWeight.Bold -> Pair("sans-serif", Typeface.BOLD) // TODO: maybe change this one to use -medium?
                FontWeight.Heavy -> Pair("sans-serif", Typeface.NORMAL)
                FontWeight.Black -> Pair("sans-serif-black", Typeface.NORMAL)
            }

            return FontFace(
                block.textFont.size,
                typefaceStyle,
                familyName,
                block.textColor.asAndroidColor(),
                when(block.textAlignment) {
                    TextAlignment.Center -> Paint.Align.CENTER
                    TextAlignment.Left -> Paint.Align.LEFT
                    TextAlignment.Right -> Paint.Align.RIGHT
                }
            )
        }
}
