/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.experiences.classic.blocks.poll.text

import android.net.Uri
import io.rover.sdk.core.data.domain.Block
import io.rover.sdk.core.data.domain.ClassicExperienceModel
import io.rover.sdk.core.data.domain.Screen
import io.rover.sdk.core.data.domain.TextPoll
import io.rover.sdk.core.streams.PublishSubject
import io.rover.sdk.experiences.classic.RectF
import io.rover.sdk.experiences.classic.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.experiences.classic.blocks.concerns.layout.Measurable
import io.rover.sdk.experiences.classic.blocks.poll.VotingInteractor
import io.rover.sdk.experiences.classic.blocks.poll.VotingState
import io.rover.sdk.experiences.classic.concerns.BindableViewModel
import io.rover.sdk.experiences.data.events.MiniAnalyticsEvent
import io.rover.sdk.experiences.data.events.Option
import io.rover.sdk.experiences.data.events.Poll
import io.rover.sdk.experiences.data.getFontAppearance
import io.rover.sdk.experiences.services.ClassicEventEmitter
import io.rover.sdk.experiences.services.MeasurementService

internal class TextPollViewModel(
    override val id: String,
    override val textPoll: TextPoll,
    private val measurementService: MeasurementService,
    override val optionBackgroundViewModel: BackgroundViewModelInterface,
    private val pollVotingInteractor: VotingInteractor,
    private val classicEventEmitter: ClassicEventEmitter,
    private val block: Block,
    private val screen: Screen,
    private val classicExperience: ClassicExperienceModel,
    private val experienceUrl: Uri?,
    private val campaignId: String?
) : TextPollViewModelInterface {

    override fun intrinsicHeight(bounds: RectF): Float {
        // Roundtrip to avoid rounding when converting floats to ints causing mismatches in measured size vs views actual size
        val optionStyleHeight = measurementService.snapToPixValue(textPoll.options.first().height)
        val verticalSpacing = measurementService.snapToPixValue(textPoll.options.first().topMargin)

        val questionHeight = measurementService.measureHeightNeededForMultiLineTextInTextView(
            textPoll.question.rawValue,
            textPoll.question.font.getFontAppearance(textPoll.question.color, textPoll.question.alignment),
            bounds.width()
        )
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
            val pollAnsweredEvent = MiniAnalyticsEvent.PollAnswered(classicExperience, experienceUrl, screen, block, option, poll, campaignId)

            classicEventEmitter.trackEvent(pollAnsweredEvent)
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
