package io.rover.sdk.ui.blocks.poll.text

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import io.rover.sdk.data.domain.TextPoll
import io.rover.sdk.data.mapToFont
import io.rover.sdk.platform.addView
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

    private val questionView = view.textView {
        setupLayoutParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private var optionViews = listOf<TextOptionView>()

    init {
        view.addView {
            questionView
        }
    }

    override var viewModelBinding: MeasuredBindableView.Binding<TextPollViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->
        binding?.viewModel?.let { viewModel ->
            bindQuestion(viewModel.textPoll)

            if (optionViews.isNotEmpty()) {
                optionViews.forEach { view.removeView(it) }
            }

            setupOptionViews(viewModel)
            informOptionBackgroundAboutSize(viewModel)

            viewModel.votingState.androidLifecycleDispose(view).subscribe({ votingState ->
                when (votingState) {
                    is VotingState.WaitingForVote -> {
                    }
                    is VotingState.Results -> setVoteResultsReceived(votingState)
                }
            }, { throw (it) }, { subscriptionCallback(it) })
        }
    }

    private fun setupOptionViews(viewModel: TextPollViewModelInterface) {
        optionViews = createOptionViews(viewModel.textPoll)
        startListeningForOptionImageUpdates(viewModel.optionBackgroundViewModel, optionViews)
        optionViews.forEachIndexed { index, optionView ->
            view.addView(optionView)
            optionView.setOnClickListener { viewModelBinding?.viewModel?.castVote(index) }
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

    private fun setVoteResultsReceived(votingResults: VotingState.Results) {
        votingResults.votingShare.forEachIndexed { index, votingShare ->
            val option = optionViews[index]
            option.setOnClickListener(null)
            val isSelectedOption = index == votingResults.selectedOption
            viewModelBinding?.viewModel?.let {
                option.goToResultsState(votingShare, isSelectedOption, it.textPoll.options[index])
            }
        }
    }

    private fun startListeningForOptionImageUpdates(
        viewModel: BackgroundViewModelInterface,
        textOptionViews: List<TextOptionView>
    ) {
        viewModel.backgroundUpdates.androidLifecycleDispose(view)
            .subscribe { (bitmap, fadeIn, backgroundImageConfiguration) ->
                val backgroundDrawable = bitmap.createBackgroundDrawable(
                    view,
                    viewModel.backgroundColor,
                    fadeIn,
                    backgroundImageConfiguration
                )
                textOptionViews.forEach { it.backgroundImage = backgroundDrawable }
            }
    }

    private fun bindQuestion(textPoll: TextPoll) {
        questionView.run {
            text = textPoll.question.rawValue
            gravity = textPoll.question.alignment.convertToGravity()
            textSize = textPoll.question.font.size.toFloat()
            setTextColor(textPoll.question.color.asAndroidColor())
            val font = textPoll.question.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }

    private fun createOptionViews(textPoll: TextPoll): List<TextOptionView> {
        return textPoll.options.map { option ->
            view.optionView {
                initializeOptionViewLayout(option)
                bindOptionView(option)
            }
        }
    }
}

internal interface ViewTextPollInterface : MeasuredBindableView<TextPollViewModelInterface>
