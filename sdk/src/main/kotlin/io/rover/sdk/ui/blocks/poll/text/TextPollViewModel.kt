package io.rover.sdk.ui.blocks.poll.text

import io.rover.sdk.data.domain.TextPoll
import io.rover.sdk.data.getFontAppearance
import io.rover.sdk.services.MeasurementService
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.ui.RectF
import io.rover.sdk.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.ui.blocks.concerns.layout.Measurable
import io.rover.sdk.ui.blocks.poll.OptionResults
import io.rover.sdk.ui.blocks.poll.VotingInteractor
import io.rover.sdk.ui.concerns.BindableViewModel

internal class TextPollViewModel(
    val id: String,
    override val textPoll: TextPoll,
    private val measurementService: MeasurementService,
    override val optionBackgroundViewModel: BackgroundViewModelInterface,
    private val pollVotingInteractor: VotingInteractor
) : TextPollViewModelInterface {

    override fun intrinsicHeight(bounds: RectF): Float {
        // Roundtrip to avoid rounding when converting floats to ints causing mismatches in measured size vs views actual size
        val optionStyleHeight = measurementService.snapToPixValue(textPoll.options.first().height)
        val verticalSpacing = measurementService.snapToPixValue(textPoll.options.first().topMargin)

        val questionHeight = measurementService.measureHeightNeededForMultiLineTextInTextView(
            textPoll.question.rawValue,
            textPoll.question.font.getFontAppearance(textPoll.question.color, textPoll.question.alignment),
            bounds.width())
        val optionsHeight = optionStyleHeight * textPoll.options.size
        val optionSpacing = verticalSpacing * (textPoll.options.size)

        return optionsHeight + optionSpacing + questionHeight
    }

    override fun castVote(selectedOption: String, optionIds: List<String>) {
        pollVotingInteractor.castVote(id, selectedOption, optionIds)
    }

    override fun checkIfAlreadyVoted(optionIds: List<String>) {
        pollVotingInteractor.checkIfAlreadyVotedAndHaveResults(id, optionIds)
    }

    override val votingState = pollVotingInteractor.votingState
}

internal sealed class VotingState {
    object WaitingForVote : VotingState()
    data class Results(val selectedOption: String, val optionResults: OptionResults) : VotingState()
}

internal interface TextPollViewModelInterface : Measurable, BindableViewModel {
    val textPoll: TextPoll
    val optionBackgroundViewModel: BackgroundViewModelInterface
    fun castVote(selectedOption: String, optionIds: List<String>)
    fun checkIfAlreadyVoted(optionIds: List<String>)
    val votingState: PublishSubject<VotingState>
}
