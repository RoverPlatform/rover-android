package io.rover.sdk.ui.blocks.poll.text

import io.rover.sdk.data.domain.TextPoll
import io.rover.sdk.data.getFontAppearance
import io.rover.sdk.services.MeasurementService
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.androidLifecycleDispose
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.RectF
import io.rover.sdk.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.ui.blocks.concerns.layout.Measurable
import io.rover.sdk.ui.blocks.poll.VotingInteractor
import io.rover.sdk.ui.concerns.BindableViewModel

internal class TextPollViewModel(
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

    override fun castVote(selectedOption: Int) {
        // TODO: Add voting logic
        setResultsState(selectedOption, listOf(100, 14, 67))
    }

    fun castVote(pollId: String, optionId: String, optionIds: List<String>) {

        //TODO: overwrite saved poll state if none
        pollVotingInteractor.castVote(pollId, optionId)

        if (pollVotingInteractor.getSavedPollState(pollId) != null) {
            //TODO: set results state
        } else {
            getPollState(pollId, optionIds)
        }
    }

    override val votingState = PublishSubject<VotingState>()

    fun getPollState(pollId: String, optionIds: List<String>) {
        val savedPollState = pollVotingInteractor.getSavedPollState(pollId)
        val noNewOptionIds = savedPollState?.first?.results?.filterKeys { it in optionIds }?.size == optionIds.size

        if(savedPollState != null && noNewOptionIds) {
            val alreadyVoted = savedPollState.second != null && savedPollState.second in optionIds

            if (alreadyVoted) {
                //TODO: set results state
            } else {

            }
        } else {
            pollVotingInteractor.fetchVotingResults(pollId, optionIds)
        }
    }

    private fun setResultsState(selectedOption: Int, votingShare: List<Int>) {
        votingState.onNext(VotingState.Results(selectedOption, votingShare))
    }
}

sealed class VotingState {
    object WaitingForVote : VotingState()
    data class Results(val selectedOption: Int, val votingShare: List<Int>) : VotingState()
}

internal interface TextPollViewModelInterface : Measurable, BindableViewModel {
    val textPoll: TextPoll
    val optionBackgroundViewModel: BackgroundViewModelInterface
    fun castVote(selectedOption: Int)
    val votingState: PublishSubject<VotingState>
}
