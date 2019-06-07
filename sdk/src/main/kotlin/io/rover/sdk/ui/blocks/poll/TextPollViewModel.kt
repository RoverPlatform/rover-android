package io.rover.sdk.ui.blocks.poll

import android.graphics.Paint
import io.rover.sdk.data.domain.Color
import io.rover.sdk.data.domain.TextAlignment
import io.rover.sdk.data.domain.TextPollBlock
import io.rover.sdk.platform.mapToFont
import io.rover.sdk.services.MeasurementService
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.ui.RectF
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.ui.blocks.concerns.layout.Measurable
import io.rover.sdk.ui.blocks.concerns.text.FontAppearance
import io.rover.sdk.ui.concerns.BindableViewModel

internal class TextPollViewModel(
    override val textPollBlock: TextPollBlock,
    private val measurementService: MeasurementService,
    override val optionBackgroundViewModel: BackgroundViewModelInterface
) : TextPollViewModelInterface{

    override fun intrinsicHeight(bounds: RectF): Float {
        // Roundtrip to avoid rounding when converting floats to ints causing mismatches in measured size vs views actual size
        val optionStyleHeight = measurementService.measureDpToPxToDp(textPollBlock.optionStyle.height)
        val borderWidth = measurementService.measureDpToPxToDp(textPollBlock.optionStyle.borderWidth)
        val verticalSpacing = measurementService.measureDpToPxToDp(textPollBlock.optionStyle.verticalSpacing)

        val questionHeight = measurementService.measureHeightNeededForMultiLineTextInTextView(
            textPollBlock.question,
            getFontAppearance(textPollBlock.questionStyle.font, textPollBlock.questionStyle.color, textPollBlock.questionStyle.textAlignment),
            bounds.width())
        val optionsHeight = ((optionStyleHeight + (borderWidth * 2)) * textPollBlock.options.size)
        val optionSpacing = verticalSpacing * (textPollBlock.options.size)

        return optionsHeight + optionSpacing + questionHeight
    }

    private fun getPaintAlignFromTextAlign(textAlignment: TextAlignment): Paint.Align {
        return when (textAlignment) {
            TextAlignment.Center -> Paint.Align.CENTER
            TextAlignment.Left -> Paint.Align.LEFT
            TextAlignment.Right -> Paint.Align.RIGHT
        }
    }

    private fun getFontAppearance(modelFont: io.rover.sdk.data.domain.Font, color: Color, alignment: TextAlignment): FontAppearance {
        val font = modelFont.weight.mapToFont()

        return FontAppearance(modelFont.size, font, color.asAndroidColor(), getPaintAlignFromTextAlign(alignment))
    }

    override fun castVote(selectedOption: Int) {
        //TODO: Add voting logic
        setResultsState(selectedOption, listOf(12, 14, 67))
    }

    override val votingState = PublishSubject<VotingState>().apply {
        onNext(VotingState.WaitingForVote)
    }

    private fun setResultsState(selectedOption: Int, votingShare: List<Int>) {
        votingState.onNext(VotingState.Results(selectedOption, votingShare))
    }
}

sealed class VotingState {
    object WaitingForVote: VotingState()
    data class Results(val selectedOption: Int, val votingShare: List<Int>): VotingState()
}

internal interface TextPollViewModelInterface : Measurable, BindableViewModel {
    val textPollBlock: TextPollBlock
    val optionBackgroundViewModel: BackgroundViewModelInterface
    fun castVote(selectedOption: Int)
    val votingState: PublishSubject<VotingState>
}