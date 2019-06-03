package io.rover.sdk.ui.blocks.poll

import android.graphics.Paint
import io.rover.sdk.data.domain.Color
import io.rover.sdk.data.domain.Font
import io.rover.sdk.data.domain.TextAlignment
import io.rover.sdk.data.domain.TextPollBlock
import io.rover.sdk.platform.mapToFont
import io.rover.sdk.services.MeasurementService
import io.rover.sdk.ui.RectF
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.concerns.layout.Measurable
import io.rover.sdk.ui.blocks.concerns.text.FontAppearance

internal class TextPollBlockViewMeasurer(
    private val textPollBlock: TextPollBlock,
    private val measurementService: MeasurementService
) : Measurable {

    override fun intrinsicHeight(bounds: RectF): Float {
        val questionHeight = measurementService.measureHeightNeededForMultiLineTextInTextView(
            textPollBlock.question,
            getFontAppearance(textPollBlock.questionStyle.font, textPollBlock.questionStyle.color, textPollBlock.questionStyle.textAlignment),
            bounds.width())
        val optionsHeight = ((textPollBlock.optionStyle.height + (textPollBlock.optionStyle.borderWidth * 2)) * textPollBlock.options.size)
        val optionSpacing = textPollBlock.optionStyle.verticalSpacing * (textPollBlock.options.size)

        return optionsHeight + optionSpacing + questionHeight
    }

    private fun getPaintAlignFromTextAlign(textAlignment: TextAlignment): Paint.Align {
        return when (textAlignment) {
            TextAlignment.Center -> Paint.Align.CENTER
            TextAlignment.Left -> Paint.Align.LEFT
            TextAlignment.Right -> Paint.Align.RIGHT
        }
    }

    private fun getFontAppearance(modelFont: Font, color: Color, alignment: TextAlignment): FontAppearance {
        val font = modelFont.weight.mapToFont()

        return FontAppearance(modelFont.size, font, color.asAndroidColor(), getPaintAlignFromTextAlign(alignment))
    }
}