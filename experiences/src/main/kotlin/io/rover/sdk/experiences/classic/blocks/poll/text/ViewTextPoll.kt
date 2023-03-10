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

import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.rover.sdk.core.data.domain.TextPoll
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.streams.androidLifecycleDispose
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.experiences.classic.asAndroidColor
import io.rover.sdk.experiences.classic.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.experiences.classic.blocks.concerns.background.createBackgroundDrawable
import io.rover.sdk.experiences.classic.blocks.poll.VotingState
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView
import io.rover.sdk.experiences.classic.concerns.MeasuredSize
import io.rover.sdk.experiences.classic.concerns.ViewModelBinding
import io.rover.sdk.experiences.data.mapToFont
import io.rover.sdk.experiences.platform.addView
import io.rover.sdk.experiences.platform.optionView
import io.rover.sdk.experiences.platform.setupLayoutParams
import io.rover.sdk.experiences.platform.textView

internal class ViewTextPoll(override val view: LinearLayout) : ViewTextPollInterface {

    private val questionView = view.textView {
        setupLayoutParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private var optionViews = mapOf<String, TextOptionView>()

    init {
        view.addView {
            questionView
        }
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override var viewModelBinding: MeasuredBindableView.Binding<TextPollViewModelInterface>? by ViewModelBinding(view, cancellationBlock = { viewModelBinding?.viewModel?.cancel() }) { binding, subscriptionCallback ->
        binding?.viewModel?.let { viewModel ->
            bindQuestion(viewModel.textPoll, viewModel.textPoll.options.size)

            if (optionViews.isNotEmpty()) {
                optionViews.forEach { view.removeView(it.value) }
            }

            setupOptionViews(viewModel)

            log.d("poll view bound")

            viewModel.votingState.subscribe({ votingState: VotingState ->
                when (votingState) {
                    is VotingState.InitialState -> {
                        setPollNotWaiting()
                    }
                    is VotingState.ResultsSeeded -> {
                        setPollNotWaiting()
                    }
                    is VotingState.SubmittingAnswer -> {
                        setVoteResultsReceived(votingState)
                        setPollNotWaiting()
                    }
                    is VotingState.RefreshingResults -> {
                        if (votingState.shouldTransition) setVoteResultUpdate(votingState)
                        setPollNotWaiting()
                    }
                    is VotingState.PollAnswered -> setPollAnsweredWaiting()
                }
            }, { e -> log.e("${e.message}") }, { subscriptionCallback(it) })

            viewModel.bindInteractor(viewModel.id, optionViews.keys.toList())
        }
    }

    private fun setPollAnsweredWaiting() {
        view.alpha = 0.5f
        optionViews.forEach { it.value.setOnClickListener(null) }
    }

    private fun setPollNotWaiting() {
        view.alpha = 1f
    }

    private fun setupOptionViews(viewModel: TextPollViewModelInterface) {
        var indexForAccessibility = 1
        optionViews = createOptionViews(viewModel.textPoll)
        startListeningForOptionImageUpdates(viewModel.optionBackgroundViewModel, optionViews)
        optionViews.forEach { (optionId, optionView) ->
            view.addView(optionView)
            optionView.setContentDescription(indexForAccessibility)
            optionView.setOnClickListener {
                viewModelBinding?.viewModel?.castVote(optionId, viewModel.textPoll.options.map { it.id })
            }
            indexForAccessibility++
        }

        informOptionBackgroundAboutSize(viewModel)
    }

    private fun bindQuestion(textPoll: TextPoll, numberOfOptions: Int) {
        questionView.run {
            text = textPoll.question.rawValue
            contentDescription = "Poll with $numberOfOptions options: ${textPoll.question.rawValue}"
            gravity = textPoll.question.alignment.convertToGravity()
            textSize = textPoll.question.font.size.toFloat()
            setTextColor(textPoll.question.color.asAndroidColor())
            val font = textPoll.question.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }

    private fun createOptionViews(textPoll: TextPoll): Map<String, TextOptionView> {
        return textPoll.options.associate { option ->
            option.id to view.optionView {
                initializeOptionViewLayout(option)
                bindOptionView(option)
            }
        }
    }

    private fun informOptionBackgroundAboutSize(viewModel: TextPollViewModelInterface) {
        viewModelBinding?.measuredSize?.width?.let { measuredWidth ->
            val optionStyleHeight = viewModel.textPoll.options.first().height.toFloat()
            val measuredSize = MeasuredSize(
                measuredWidth,
                optionStyleHeight,
                view.resources.displayMetrics.density
            )
            viewModel.optionBackgroundViewModel.informDimensions(measuredSize)
        }
    }

    private fun setVoteResultUpdate(votingUpdate: VotingState.RefreshingResults) {
        val maxVotingValue = votingUpdate.optionResults.results.map { it.value }.max() ?: 0

        votingUpdate.optionResults.results.forEach { (id, votingShare) ->
            val option = optionViews[id]
            option?.setOnClickListener(null)
            val isSelectedOption = id == votingUpdate.selectedOption

            viewModelBinding?.viewModel?.let {
                if (votingUpdate.shouldTransition) {
                    if (votingUpdate.shouldAnimate) {
                        option?.updateResults(votingShare)
                    } else {
                        option?.goToResultsState(
                            votingShare,
                            isSelectedOption,
                            it.textPoll.options.find { it.id == id }!!,
                            false,
                            viewModelBinding?.measuredSize?.width,
                            maxVotingValue
                        )
                    }
                }
            }
        }
    }

    private fun setVoteResultsReceived(votingResults: VotingState.SubmittingAnswer) {
        val maxVotingValue = votingResults.optionResults.results.map { it.value }.max() ?: 0

        votingResults.optionResults.results.forEach { (id, votingShare) ->
            val option = optionViews[id]
            option?.setOnClickListener(null)
            val isSelectedOption = id == votingResults.selectedOption

            viewModelBinding?.viewModel?.let {
                option?.goToResultsState(
                    votingShare,
                    isSelectedOption,
                    it.textPoll.options.find { it.id == id }!!,
                    votingResults.shouldAnimate,
                    viewModelBinding?.measuredSize?.width,
                    maxVotingValue
                )
            }
        }
    }

    private fun startListeningForOptionImageUpdates(
        viewModel: BackgroundViewModelInterface,
        textOptionViews: Map<String, TextOptionView>
    ) {
        viewModel.backgroundUpdates.androidLifecycleDispose(view)
            .subscribe { (bitmap, fadeIn, backgroundImageConfiguration) ->
                textOptionViews.forEach {
                    val backgroundDrawable = bitmap.createBackgroundDrawable(
                        view,
                        viewModel.backgroundColor,
                        fadeIn,
                        backgroundImageConfiguration
                    )
                    it.value.backgroundImage = backgroundDrawable
                }
            }
    }
}

internal interface ViewTextPollInterface : MeasuredBindableView<TextPollViewModelInterface>
