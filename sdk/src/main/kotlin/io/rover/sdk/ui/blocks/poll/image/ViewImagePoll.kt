package io.rover.sdk.ui.blocks.poll.image

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import io.rover.sdk.data.domain.ImagePollBlock
import io.rover.sdk.data.mapToFont
import io.rover.sdk.logging.log
import io.rover.sdk.platform.imageOptionView
import io.rover.sdk.platform.setupLayoutParams
import io.rover.sdk.platform.textView
import io.rover.sdk.streams.androidLifecycleDispose
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.poll.VotingState
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
    private lateinit var pollOptionViews: List<ImagePollOptionView>

    init {
        view.addView(questionView)
    }

    companion object {
        private const val OPTION_TEXT_HEIGHT = 40f
    }

    override var viewModelBinding: MeasuredBindableView.Binding<ImagePollViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->

        binding?.viewModel?.let { viewModel ->
            val width = binding.measuredSize?.width ?: 0f

            val verticalSpacing = viewModel.imagePollBlock.optionStyle.verticalSpacing

            val imageLength =
                (width.dpAsPx(view.resources.displayMetrics) - verticalSpacing.dpAsPx(view.resources.displayMetrics)) / 2

            bindQuestion(viewModel.imagePollBlock)

            setupOptionViews(viewModel, imageLength)

            viewModel.multiImageUpdates.androidLifecycleDispose(this.view).subscribe({ imageList ->
                pollOptionViews.forEachIndexed { index, imageOptionView ->
                    imageOptionView.bindOptionImage(imageList[index].bitmap)
                }
            }, { error -> log.w("Problem fetching poll images: $error, ignoring.") }, { subscription -> subscriptionCallback(subscription) })

            viewModel.informImagePollOptionDimensions(
                MeasuredSize(
                    imageLength.pxAsDp(view.resources.displayMetrics),
                    imageLength.pxAsDp(view.resources.displayMetrics),
                    view.resources.displayMetrics.density
                )
            )

            viewModel.votingState.androidLifecycleDispose(view).subscribe({ votingState ->
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
            val option = pollOptionViews[index]
            option.setOnClickListener(null)
            val isSelectedOption = index == votingResults.selectedOption
            viewModelBinding?.viewModel?.let {
                option.goToResultsState(votingShare, isSelectedOption, it.imagePollBlock.optionStyle)
            }
        }
    }

    private fun bindQuestion(imagePollBlock: ImagePollBlock) {
        questionView.run {
            text = imagePollBlock.question
            gravity = imagePollBlock.questionStyle.textAlignment.convertToGravity()
            textSize = imagePollBlock.questionStyle.font.size.toFloat()
            setTextColor(imagePollBlock.questionStyle.color.asAndroidColor())
            val font = imagePollBlock.questionStyle.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }

    private fun setupOptionViews(viewModel: ImagePollViewModelInterface, imageLength: Int) {
        pollOptionViews = createOptionViews(viewModel.imagePollBlock, imageLength)

        when {
            pollOptionViews.size == 2 -> createTwoOptionLayout()
            pollOptionViews.size == 4 -> createFourOptionLayout()
        }
    }

    private fun createTwoOptionLayout() {
        val row = LinearLayout(view.context)
        view.addView(row)
        pollOptionViews.forEachIndexed { index, imagePollOptionView ->
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

        pollOptionViews.forEachIndexed { index, imagePollOptionView ->
            val isOnFirstRow = index < 2
            if (isOnFirstRow) row1.addView(imagePollOptionView) else row2.addView(imagePollOptionView)
            imagePollOptionView.setOnClickListener { viewModelBinding?.viewModel?.castVote(index) }
        }
    }

    private fun createOptionViews(imagePollBlock: ImagePollBlock, imageLength: Int): List<ImagePollOptionView> {
        val optionTextHeight = OPTION_TEXT_HEIGHT.dpAsPx(view.resources.displayMetrics)
        val horizontalSpacing =
            imagePollBlock.optionStyle.horizontalSpacing.dpAsPx(view.resources.displayMetrics)
        val verticalSpacing =
            imagePollBlock.optionStyle.verticalSpacing.dpAsPx(view.resources.displayMetrics)

        return imagePollBlock.options.mapIndexed { index, option ->
            val isInFirstColumn = index == 0 || index == 2

            view.imageOptionView {
                initializeOptionViewLayout(imagePollBlock.optionStyle)
                bindOptionView(option.text, imagePollBlock.optionStyle)
                bindOptionImageSize(imageLength)
                setupLayoutParams(
                    width = imageLength,
                    height = imageLength + optionTextHeight,
                    rightMargin = if (isInFirstColumn) horizontalSpacing else 0,
                    topMargin = verticalSpacing
                )
            }
        }
    }
}

internal interface ViewImagePollInterface : MeasuredBindableView<ImagePollViewModelInterface>