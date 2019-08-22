package io.rover.sdk.ui.blocks.poll.text

import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.rover.sdk.data.domain.TextPoll
import io.rover.sdk.data.mapToFont
import io.rover.sdk.logging.log
import io.rover.sdk.platform.addView
import io.rover.sdk.platform.optionView
import io.rover.sdk.platform.setupLayoutParams
import io.rover.sdk.platform.textView
import io.rover.sdk.streams.ViewEvent
import io.rover.sdk.streams.androidLifecycleDispose
import io.rover.sdk.streams.attachEvents
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.ui.blocks.concerns.background.createBackgroundDrawable
import io.rover.sdk.ui.blocks.poll.RefreshEvent
import io.rover.sdk.ui.blocks.poll.VotingState
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.MeasuredSize
import io.rover.sdk.ui.concerns.ViewModelBinding
import org.reactivestreams.Subscription
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

internal class ViewTextPoll(override val view: LinearLayout) : ViewTextPollInterface {

    private val questionView = view.textView {
        setupLayoutParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    companion object {
        private const val UPDATE_INTERVAL = 5000L
    }

    private var optionViews = mapOf<String, TextOptionView>()

    init {
        view.addView {
            questionView
        }
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private var timer: Timer? = null
    set(value) {
        field?.cancel()
        field?.purge()
        field = value
    }

    override var viewModelBinding: MeasuredBindableView.Binding<TextPollViewModelInterface>? by ViewModelBinding(view, cancellationBlock = {timer = null}) { binding, subscriptionCallback ->
        binding?.viewModel?.let { viewModel ->
            bindQuestion(viewModel.textPoll, viewModel.textPoll.options.size)

            if (optionViews.isNotEmpty()) {
                optionViews.forEach { view.removeView(it.value) }
            }

            setupOptionViews(viewModel)

            log.d("poll view bound")

            viewModel.votingState.subscribe({ votingState : VotingState ->
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
                        if (votingState.pollId == viewModel.id) setUpdateTimer(votingState)
                        setPollNotWaiting()
                    }
                    is VotingState.PollAnswered -> setPollAnsweredWaiting()
                }
            }, { e -> log.e("${e.message}") }, { subscriptionCallback(it) })

            viewModel.refreshEvents.subscribe({ refresh ->
                if (refresh.pollId == viewModel.id) setVoteResultUpdate(refresh)
            }, { e -> log.e("${e.message}") }, { subscriptionCallback(it) })

            viewModel.bindInteractor(viewModel.id, optionViews.keys.toList())
        }
    }
    private fun createTimer(votingState: VotingState.RefreshingResults): Timer {
        return fixedRateTimer(period = UPDATE_INTERVAL, initialDelay = UPDATE_INTERVAL) {
            if(view.windowVisibility == View.VISIBLE) {
                viewModelBinding?.viewModel?.checkForUpdate(votingState.pollId, votingState.optionResults.results.keys.toList())
            }
        }
    }

    private fun setPollAnsweredWaiting() {
        view.alpha = 0.5f
    }

    private fun setPollNotWaiting() {
        view.alpha = 1f
    }

    private fun setUpdateTimer(votingState: VotingState.RefreshingResults) {
        if (timer == null) { timer = createTimer(votingState) }
    }

    private fun setupOptionViews(viewModel: TextPollViewModelInterface) {

        var indexForAccessibility = 1
        optionViews = createOptionViews(viewModel.textPoll)
        startListeningForOptionImageUpdates(viewModel.optionBackgroundViewModel, optionViews)
        optionViews.forEach { (optionId, optionView) ->
            view.addView(optionView)
            optionView.setContentDescription(indexForAccessibility)
            optionView.setOnClickListener {
                viewModelBinding?.viewModel?.castVote(optionId, viewModel.textPoll.options.map { it.id }) }
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

    private fun setVoteResultUpdate(votingUpdate: RefreshEvent) {
        votingUpdate.optionResults.results.forEach { (id, votingShare) ->
            val option = optionViews[id]

            viewModelBinding?.viewModel?.let {
                option?.updateResults(votingShare)
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
                option?.goToResultsState(votingShare, isSelectedOption, it.textPoll.options.find { it.id == id }!!, votingResults.shouldAnimate,
                    viewModelBinding?.measuredSize?.width, maxVotingValue)
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
