package io.rover.sdk.ui.blocks.poll.image

import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.rover.sdk.data.domain.ImagePoll
import io.rover.sdk.data.mapToFont
import io.rover.sdk.logging.log
import io.rover.sdk.platform.imageOptionView
import io.rover.sdk.platform.setupLayoutParams
import io.rover.sdk.platform.setupLinearLayoutParams
import io.rover.sdk.platform.textView
import io.rover.sdk.streams.androidLifecycleDispose
import io.rover.sdk.streams.distinctUntilChanged
import io.rover.sdk.streams.first
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.poll.RefreshEvent
import io.rover.sdk.ui.blocks.poll.VotingState
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.MeasuredSize
import io.rover.sdk.ui.concerns.ViewModelBinding
import io.rover.sdk.ui.dpAsPx
import io.rover.sdk.ui.pxAsDp
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

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
        view.addView(questionView)
    }

    companion object {
        private const val OPTION_TEXT_HEIGHT = 40f
        private const val UPDATE_INTERVAL = 5000L
    }

    private var timer: Timer? = null
        set(value) {
            field?.cancel()
            field?.purge()
            field = value
        }

    private fun setPollAnsweredWaiting() {
        view.alpha = 0.5f
    }

    private fun setPollNotWaiting() {
        view.alpha = 1f
    }

    override var viewModelBinding: MeasuredBindableView.Binding<ImagePollViewModelInterface>? by ViewModelBinding(view = view, cancellationBlock = {timer = null}) { binding, subscriptionCallback ->

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

            bindQuestion(viewModel.imagePoll)
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
                { subscription -> subscriptionCallback(subscription) })
            
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
                        if (votingState.pollId == viewModel.id) setUpdateTimer(votingState)
                        setPollNotWaiting()
                    }
                }
            }, { throw (it) }, { subscriptionCallback(it) })

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

    private fun setUpdateTimer(votingState: VotingState.RefreshingResults) {
        if (timer == null) timer = createTimer(votingState)
    }

    private fun setVoteResultUpdate(votingUpdate: RefreshEvent) {
        votingUpdate.optionResults.results.forEach { (id, votingShare) ->
            val option = optionViews[id]

            viewModelBinding?.viewModel?.let {
                option?.updateResults(votingShare)
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
        view.addView(row1)
        optionViews.forEach { (id, imagePollOptionView) ->
            row1.addView(imagePollOptionView)
            imagePollOptionView.setOnClickListener {
                viewModelBinding?.viewModel?.castVote(id, optionViews.keys.toList())
            }
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
            viewsAdded ++
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