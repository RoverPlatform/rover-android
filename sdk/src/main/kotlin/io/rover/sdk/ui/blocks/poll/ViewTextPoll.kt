package io.rover.sdk.ui.blocks.poll

import android.graphics.Typeface
import android.support.v4.view.ViewCompat
import android.support.v7.widget.AppCompatTextView
import android.view.ViewGroup
import android.widget.LinearLayout
import io.rover.sdk.data.domain.QuestionStyle
import io.rover.sdk.data.domain.TextPollBlock
import io.rover.sdk.platform.addView
import io.rover.sdk.platform.mapToFont
import io.rover.sdk.platform.optionView
import io.rover.sdk.platform.setupLayoutParams
import io.rover.sdk.platform.textView
import io.rover.sdk.streams.androidLifecycleDispose
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.ui.blocks.concerns.background.createBackgroundDrawable
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.MeasuredSize
import io.rover.sdk.ui.concerns.ViewModelBinding

internal class ViewTextPoll(override val view: LinearLayout) : ViewTextPollInterface {

    companion object {
        private const val MAX_OPTIONS_AMOUNT = 4
    }

    private val optionIds = (0 until MAX_OPTIONS_AMOUNT).map { ViewCompat.generateViewId() }
    private val questionId = ViewCompat.generateViewId()

    override var viewModelBinding: MeasuredBindableView.Binding<TextPollViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->
        binding?.viewModel?.let { viewModel ->
            if (view.childCount == 0) {
                view.addView {
                    view.textView {
                        id = questionId
                        setupLayoutParams(width = ViewGroup.LayoutParams.MATCH_PARENT, height = ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                }
            }

            bindQuestion(viewModel.textPollBlock)
            setupOptionViews(viewModel)
            informOptionBackgroundAboutSize(viewModel)

            viewModel.votingState.androidLifecycleDispose(view).subscribe ({ votingState ->
                when (votingState) {
                    is VotingState.WaitingForVote -> { }
                    is VotingState.Results -> setVoteResultsReceived(votingState)
                }
            }, { throw (it) }, { subscriptionCallback(it) })
        }
    }

    private fun setupOptionViews(viewModel: TextPollViewModelInterface) {
        val optionViews = createOptionViews(viewModel.textPollBlock)
        startListeningForOptionImageUpdates(viewModel.optionBackgroundViewModel, optionViews)
        optionViews.forEachIndexed { index, optionView ->
            view.addView(optionView)
            optionView.setOnClickListener { viewModelBinding?.viewModel?.castVote(index) }
        }
    }

    private fun informOptionBackgroundAboutSize(viewModel: TextPollViewModelInterface) {
        viewModelBinding?.measuredSize?.width?.let { measuredWidth ->
            val optionStyleHeight = viewModel.textPollBlock.optionStyle.height.toFloat()
            val measuredSize = MeasuredSize(measuredWidth, optionStyleHeight, view.resources.displayMetrics.density)
            viewModel.optionBackgroundViewModel.informDimensions(measuredSize)
        }
    }

    private fun setVoteResultsReceived(votingResults: VotingState.Results) {
        votingResults.votingShare.forEachIndexed { index, votingShare ->
            val option = view.findViewById<TextOptionView>(optionIds[index])
            val isSelectedOption = index == votingResults.selectedOption
            viewModelBinding?.viewModel?.let {
                option.goToResultsState(votingShare, isSelectedOption, it.textPollBlock.optionStyle)
            }
        }
    }

    private fun startListeningForOptionImageUpdates(
        viewModel: BackgroundViewModelInterface, textOptionViews: List<TextOptionView>
    ) {
        viewModel.backgroundUpdates.androidLifecycleDispose(view)
            .subscribe { (bitmap, fadeIn, backgroundImageConfiguration) ->
                val backgroundDrawable = bitmap.createBackgroundDrawable(view, viewModel.backgroundColor, fadeIn, backgroundImageConfiguration)
                textOptionViews.forEach { it.backgroundImage = backgroundDrawable }
            }
    }

    private fun bindQuestion(textPollBlock: TextPollBlock) {
        view.findViewById<AppCompatTextView>(questionId).run {
            text = textPollBlock.question
            gravity = textPollBlock.questionStyle.textAlignment.convertToGravity()
            textSize = textPollBlock.questionStyle.font.size.toFloat()
            setTextColor(textPollBlock.questionStyle.color.asAndroidColor())
            val font = textPollBlock.questionStyle.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }

    private fun createOptionViews(textPollBlock: TextPollBlock): List<TextOptionView> {
        return textPollBlock.options.mapIndexed { index, option ->
            view.optionView {
                id = optionIds[index]
                initializeOptionViewLayout(textPollBlock.optionStyle)
                bindOptionView(option, textPollBlock.optionStyle)
            }
        }
    }
}

internal interface ViewTextPollInterface : MeasuredBindableView<TextPollViewModelInterface>
