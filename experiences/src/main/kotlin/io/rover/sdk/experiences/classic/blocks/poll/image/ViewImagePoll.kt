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

package io.rover.sdk.experiences.classic.blocks.poll.image

import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.rover.sdk.core.data.domain.ImagePoll
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.streams.distinctUntilChanged
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.experiences.classic.asAndroidColor
import io.rover.sdk.experiences.classic.blocks.poll.VotingState
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView
import io.rover.sdk.experiences.classic.concerns.MeasuredSize
import io.rover.sdk.experiences.classic.concerns.ViewModelBinding
import io.rover.sdk.experiences.classic.dpAsPx
import io.rover.sdk.experiences.classic.pxAsDp
import io.rover.sdk.experiences.data.mapToFont
import io.rover.sdk.experiences.platform.imageOptionView
import io.rover.sdk.experiences.platform.setupLayoutParams
import io.rover.sdk.experiences.platform.setupLinearLayoutParams
import io.rover.sdk.experiences.platform.textView

internal class ViewImagePoll(override val view: LinearLayout) :
    ViewImagePollInterface {
    private val questionView = view.textView {
        setupLayoutParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    private var optionViews = mapOf<String, ImagePollOptionView>()

    init {
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        view.addView(questionView)
    }

    companion object {
        private const val OPTION_TEXT_HEIGHT = 40f
    }

    private fun setPollAnsweredWaiting() {
        view.alpha = 0.5f
        optionViews.forEach { it.value.setOnClickListener(null) }
    }

    private fun setPollNotWaiting() {
        view.alpha = 1f
    }

    override var viewModelBinding: MeasuredBindableView.Binding<ImagePollViewModelInterface>? by ViewModelBinding(view = view, cancellationBlock = { viewModelBinding?.viewModel?.cancel() }) { binding, subscriptionCallback ->

        binding?.viewModel?.let { viewModel ->
            val width = binding.measuredSize?.width ?: 0f

            val horizontalSpacing = viewModel.imagePoll.options[1].leftMargin

            val borderWidth = viewModel.imagePoll.options.first().border.width.dpAsPx(view.resources.displayMetrics)

            val imageLength =
                ((width.dpAsPx(view.resources.displayMetrics) - horizontalSpacing.dpAsPx(view.resources.displayMetrics)) / 2) - (borderWidth * 2)

            if (optionViews.isNotEmpty()) {
                row1.removeAllViews()
                row2.removeAllViews()
                view.removeView(row1)
                view.removeView(row2)
            }

            bindQuestion(viewModel.imagePoll, viewModel.imagePoll.options.size)
            setupOptionViews(viewModel, imageLength)

            viewModel.multiImageUpdates.distinctUntilChanged().subscribe(
                { imageList ->
                    optionViews.forEach { (index, imageOptionView) ->
                        imageList[index]?.let {
                            imageOptionView.bindOptionImage(it.bitmap, it.shouldFade, viewModel.imagePoll.options.first().opacity.toFloat())
                        }
                    }
                },
                { error -> log.w("Problem fetching poll images: $error, ignoring.") },
                { subscription -> subscriptionCallback(subscription) }
            )

            viewModel.informImagePollOptionDimensions(
                MeasuredSize(
                    imageLength.pxAsDp(view.resources.displayMetrics),
                    imageLength.pxAsDp(view.resources.displayMetrics),
                    view.resources.displayMetrics.density
                )
            )

            viewModel.votingState.subscribe({ votingState: VotingState ->
                when (votingState) {
                    is VotingState.InitialState -> {
                        setPollNotWaiting()
                    }
                    is VotingState.ResultsSeeded -> {
                        setPollNotWaiting()
                    }
                    is VotingState.PollAnswered -> {
                        setPollAnsweredWaiting()
                    }
                    is VotingState.SubmittingAnswer -> {
                        setVoteResultsReceived(votingState, imageLength)
                        setPollNotWaiting()
                    }
                    is VotingState.RefreshingResults -> {
                        if (votingState.shouldTransition) setVoteResultUpdate(votingState, imageLength)
                        setPollNotWaiting()
                    }
                }
            }, { throw (it) }, { subscriptionCallback(it) })

            viewModel.bindInteractor(viewModel.id, optionViews.keys.toList())
        }
    }

    private fun setVoteResultUpdate(votingUpdate: VotingState.RefreshingResults, viewWidth: Int) {
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
                            it.imagePoll.options.find { it.id == id }!!,
                            false,
                            viewWidth
                        )
                    }
                }
            }
        }
    }

    private fun setVoteResultsReceived(votingResults: VotingState.SubmittingAnswer, viewWidth: Int) {
        votingResults.optionResults.results.forEach { (id, votingShare) ->
            val option = optionViews[id]
            option?.setOnClickListener(null)
            val isSelectedOption = id == votingResults.selectedOption
            viewModelBinding?.viewModel?.let {
                option?.goToResultsState(votingShare, isSelectedOption, it.imagePoll.options.find { it.id == id }!!, votingResults.shouldAnimate, viewWidth)
            }
        }
    }

    private fun bindQuestion(imagePoll: ImagePoll, numberOfOptions: Int) {
        questionView.run {
            text = imagePoll.question.rawValue
            contentDescription = "Poll with $numberOfOptions options: ${imagePoll.question.rawValue}"
            gravity = imagePoll.question.alignment.convertToGravity()
            textSize = imagePoll.question.font.size.toFloat()
            setTextColor(imagePoll.question.color.asAndroidColor())
            val font = imagePoll.question.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }

    private fun setupOptionViews(viewModel: ImagePollViewModelInterface, imageLength: Int) {
        optionViews = createOptionViews(viewModel.imagePoll, imageLength)

        when {
            optionViews.size == 2 -> createTwoOptionLayout()
            optionViews.size == 4 -> createFourOptionLayout()
        }
    }

    private fun createTwoOptionLayout() {
        view.addView(row1)

        var indexForAccessibility = 1
        optionViews.forEach { (id, imagePollOptionView) ->
            row1.addView(imagePollOptionView)
            imagePollOptionView.setContentDescription(indexForAccessibility)
            imagePollOptionView.setOnClickListener {
                viewModelBinding?.viewModel?.castVote(id, optionViews.keys.toList())
            }
            indexForAccessibility++
        }
    }

    private val row1 = LinearLayout(view.context)
    private val row2 = LinearLayout(view.context)

    private fun createFourOptionLayout() {
        view.addView(row1)
        view.addView(row2)

        var viewsAdded = 0
        optionViews.forEach { (id, imagePollOptionView) ->
            val isOnFirstRow = viewsAdded < 2
            if (isOnFirstRow) row1.addView(imagePollOptionView) else row2.addView(imagePollOptionView)
            viewsAdded++
            imagePollOptionView.setContentDescription(viewsAdded)
            imagePollOptionView.setOnClickListener { viewModelBinding?.viewModel?.castVote(id, optionViews.keys.toList()) }
        }
    }

    private fun createOptionViews(imagePoll: ImagePoll, imageLength: Int): Map<String, ImagePollOptionView> {
        val borderWidth = imagePoll.options.first().border.width.dpAsPx(view.resources.displayMetrics)
        val totalBorderWidth = borderWidth * 2

        return imagePoll.options.associate {
            it.id to view.imageOptionView {
                initializeOptionViewLayout(it, imageLength + OPTION_TEXT_HEIGHT.toInt() + totalBorderWidth)
                bindOptionView(it)
                bindOptionImageSize(imageLength)
                setupLinearLayoutParams(
                    width = imageLength + totalBorderWidth,
                    height = imageLength + OPTION_TEXT_HEIGHT.dpAsPx(view.resources.displayMetrics) + totalBorderWidth,
                    leftMargin = it.leftMargin.dpAsPx(view.resources.displayMetrics),
                    topMargin = it.topMargin.dpAsPx(view.resources.displayMetrics)
                )
            }
        }
    }
}

internal interface ViewImagePollInterface : MeasuredBindableView<ImagePollViewModelInterface>
