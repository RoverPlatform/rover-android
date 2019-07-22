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

    private var optionViews = mapOf<String, TextOptionView>()

    init {
        view.addView {
            questionView
        }
    }

    override var viewModelBinding: MeasuredBindableView.Binding<TextPollViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->
        binding?.viewModel?.let { viewModel ->
            bindQuestion(viewModel.textPoll)

            if (optionViews.isNotEmpty()) {
                optionViews.forEach { view.removeView(it.value) }
            }

            setupOptionViews(viewModel)

            viewModel.votingState.subscribe({ votingState ->
                when (votingState) {
                    is VotingState.WaitingForVote -> { }
                    is VotingState.Results -> setVoteResultsReceived(votingState)
                }
            }, { throw (it) }, { subscriptionCallback(it) })

            viewModel.checkIfAlreadyVoted(optionViews.keys.toList())
        }
    }

    private fun setupOptionViews(viewModel: TextPollViewModelInterface) {
        optionViews = createOptionViews(viewModel.textPoll)
        startListeningForOptionImageUpdates(viewModel.optionBackgroundViewModel, optionViews)
        optionViews.forEach { (optionId, optionView) ->
            view.addView(optionView)
            optionView.setOnClickListener {
                viewModelBinding?.viewModel?.castVote(optionId, viewModel.textPoll.options.map { it.id }) }
        }

        informOptionBackgroundAboutSize(viewModel)
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
        votingResults.optionResults.results.forEach { (id, votingShare) ->
            val option = optionViews[id]
            option?.setOnClickListener(null)
            val isSelectedOption = id == votingResults.selectedOption
            viewModelBinding?.viewModel?.let {
                option?.goToResultsState(votingShare, isSelectedOption, it.textPoll.options.first())
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

    private fun createOptionViews(textPoll: TextPoll): Map<String, TextOptionView> {
        return textPoll.options.associate { option ->
            option.id to view.optionView {
                initializeOptionViewLayout(option)
                bindOptionView(option)
            }
        }
    }
}

internal interface ViewTextPollInterface : MeasuredBindableView<TextPollViewModelInterface>
