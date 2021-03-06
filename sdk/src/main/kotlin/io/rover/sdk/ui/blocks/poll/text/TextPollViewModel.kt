package io.rover.sdk.ui.blocks.poll.text

import io.rover.sdk.data.domain.Block
import io.rover.sdk.data.domain.Experience
import io.rover.sdk.data.domain.Screen
import io.rover.sdk.data.domain.TextPoll
import io.rover.sdk.data.events.Option
import io.rover.sdk.data.events.Poll
import io.rover.sdk.data.events.RoverEvent
import io.rover.sdk.data.getFontAppearance
import io.rover.sdk.services.EventEmitter
import io.rover.sdk.services.MeasurementService
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.ui.RectF
import io.rover.sdk.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.ui.blocks.concerns.layout.Measurable
import io.rover.sdk.ui.blocks.poll.VotingInteractor
import io.rover.sdk.ui.blocks.poll.VotingState
import io.rover.sdk.ui.concerns.BindableViewModel

internal class TextPollViewModel(
    override val id: String,
    override val textPoll: TextPoll,
    private val measurementService: MeasurementService,
    override val optionBackgroundViewModel: BackgroundViewModelInterface,
    private val pollVotingInteractor: VotingInteractor,
    private val eventEmitter: EventEmitter,
    private val block: Block,
    private val screen: Screen,
    private val experience: Experience,
    private val campaignId: String?
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

        val borderWidth = measurementService.snapToPixValue(textPoll.options.first().border.width)

        val totalBorderWidth = borderWidth * textPoll.options.size * 2

        return optionsHeight + optionSpacing + questionHeight + totalBorderWidth
    }

    override fun castVote(selectedOption: String, optionIds: List<String>) {
        pollVotingInteractor.castVotes(selectedOption)
        trackPollAnswered(selectedOption)
    }

    private fun trackPollAnswered(selectedOption: String) {
        val selectedPollOption = textPoll.options.find { it.id == selectedOption }

        selectedPollOption?.let { selectedPollOption ->
            val option = Option(selectedPollOption.id, selectedPollOption.text.rawValue)
            val poll = Poll(id, textPoll.question.rawValue)
            val pollAnsweredEvent = RoverEvent.PollAnswered(experience, screen, block, option, poll, campaignId)

            eventEmitter.trackEvent(pollAnsweredEvent)
        }
    }

    override fun bindInteractor(id: String, optionIds: List<String>) {
        pollVotingInteractor.initialize(id, optionIds)
    }

    override fun cancel() {
        pollVotingInteractor.cancel()
    }

    override val votingState = pollVotingInteractor.votingState
}

internal interface TextPollViewModelInterface : Measurable, BindableViewModel {
    val textPoll: TextPoll
    val id: String
    fun cancel()
    val optionBackgroundViewModel: BackgroundViewModelInterface
    fun castVote(selectedOption: String, optionIds: List<String>)
    fun bindInteractor(id: String, optionIds: List<String>)
    val votingState: PublishSubject<VotingState>
}
