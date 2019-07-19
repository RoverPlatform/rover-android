package io.rover.sdk.ui.blocks.poll.image

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import io.rover.sdk.data.domain.ImagePoll
import io.rover.sdk.data.mapToFont
import io.rover.sdk.logging.log
import io.rover.sdk.platform.imageOptionView
import io.rover.sdk.platform.setupLayoutParams
import io.rover.sdk.platform.textView
import io.rover.sdk.streams.androidLifecycleDispose
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.poll.text.VotingState
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.MeasuredSize
import io.rover.sdk.ui.concerns.ViewModelBinding
import io.rover.sdk.ui.dpAsPx
import io.rover.sdk.ui.pxAsDp

internal class ViewImagePoll(override val view: LinearLayout) :
    ViewImagePollInterface {
    private val questionView = view.textView {
        setupLayoutParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    private var optionViews = listOf<ImagePollOptionView>()

    init {
        view.addView(questionView)
    }

    companion object {
        private const val OPTION_TEXT_HEIGHT = 40f
    }

    override var viewModelBinding: MeasuredBindableView.Binding<ImagePollViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->

        binding?.viewModel?.let { viewModel ->
            val width = binding.measuredSize?.width ?: 0f

            val horizontalSpacing = viewModel.imagePoll.options[1].leftMargin

            val imageLength =
                (width.dpAsPx(view.resources.displayMetrics) - horizontalSpacing.dpAsPx(view.resources.displayMetrics)) / 2

            bindQuestion(viewModel.imagePoll)
            if (optionViews.isNotEmpty()) {
                optionViews.forEach { view.removeView(it) }
            }
            setupOptionViews(viewModel, imageLength)

            viewModel.multiImageUpdates.subscribe(
                { imageList ->
                    optionViews.forEachIndexed { index, imageOptionView ->
                        imageOptionView.bindOptionImage(imageList[index].bitmap, imageList[index].shouldFade, viewModel.imagePoll.options[index].opacity.toFloat())
                    }
                },
                { error -> log.w("Problem fetching poll images: $error, ignoring.") },
                { subscription -> subscriptionCallback(subscription) })

            viewModel.informImagePollOptionDimensions(
                MeasuredSize(
                    imageLength.pxAsDp(view.resources.displayMetrics),
                    imageLength.pxAsDp(view.resources.displayMetrics),
                    view.resources.displayMetrics.density
                )
            )

            viewModel.votingState.subscribe({ votingState ->
                when (votingState) {
                    is VotingState.WaitingForVote -> {
                    }
                    is VotingState.Results -> setVoteResultsReceived(votingState)
                }
            }, { throw (it) }, { subscriptionCallback(it) })

            binding.viewModel
        }
    }

    private fun setVoteResultsReceived(votingResults: VotingState.Results) {
        votingResults.votingShare.forEachIndexed { index, votingShare ->
            val option = optionViews[index]
            option.setOnClickListener(null)
            val isSelectedOption = index == votingResults.selectedOption
            viewModelBinding?.viewModel?.let {
                option.goToResultsState(votingShare, isSelectedOption, it.imagePoll.options[index])
            }
        }
    }

    private fun bindQuestion(imagePoll: ImagePoll) {
        questionView.run {
            text = imagePoll.question.rawValue
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
        val row = LinearLayout(view.context)
        view.addView(row)
        optionViews.forEachIndexed { index, imagePollOptionView ->
            row.addView(imagePollOptionView)
            imagePollOptionView.setOnClickListener {
                viewModelBinding?.viewModel?.castVote(index)
            }
        }
    }

    private fun createFourOptionLayout() {
        val row1 = LinearLayout(view.context)
        val row2 = LinearLayout(view.context)
        view.addView(row1)
        view.addView(row2)

        optionViews.forEachIndexed { index, imagePollOptionView ->
            val isOnFirstRow = index < 2
            if (isOnFirstRow) row1.addView(imagePollOptionView) else row2.addView(imagePollOptionView)
            imagePollOptionView.setOnClickListener { viewModelBinding?.viewModel?.castVote(index) }
        }
    }

    private fun createOptionViews(imagePoll: ImagePoll, imageLength: Int): List<ImagePollOptionView> {
        val optionTextHeight = OPTION_TEXT_HEIGHT.dpAsPx(view.resources.displayMetrics)

        return imagePoll.options.mapIndexed { index, option ->
            view.imageOptionView {
                initializeOptionViewLayout(option)
                bindOptionView(option)
                bindOptionImageSize(imageLength)
                setupLayoutParams(
                    width = imageLength,
                    height = imageLength + optionTextHeight,
                    leftMargin = option.leftMargin.dpAsPx(view.resources.displayMetrics),
                    topMargin = option.topMargin.dpAsPx(view.resources.displayMetrics)
                )
            }
        }
    }
}

internal interface ViewImagePollInterface : MeasuredBindableView<ImagePollViewModelInterface>