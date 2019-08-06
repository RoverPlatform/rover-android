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
import io.rover.sdk.streams.ViewEvent
import io.rover.sdk.streams.attachEvents
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.poll.text.VotingState
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.MeasuredSize
import io.rover.sdk.ui.concerns.ViewModelBinding
import io.rover.sdk.ui.dpAsPx
import io.rover.sdk.ui.pxAsDp
import org.reactivestreams.Subscription
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

    override var viewModelBinding: MeasuredBindableView.Binding<ImagePollViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->

        binding?.viewModel?.let { viewModel ->
            val width = binding.measuredSize?.width ?: 0f

            val horizontalSpacing = viewModel.imagePoll.options[1].leftMargin

            val imageLength =
                (width.dpAsPx(view.resources.displayMetrics) - horizontalSpacing.dpAsPx(view.resources.displayMetrics)) / 2

            if (optionViews.isNotEmpty()) {
                row1.removeAllViews()
                row2.removeAllViews()
                view.removeView(row1)
                view.removeView(row2)
            }

            bindQuestion(viewModel.imagePoll)
            setupOptionViews(viewModel, imageLength)

            viewModel.multiImageUpdates.subscribe(
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

            viewModel.votingState.subscribe({ votingState ->
                when (votingState) {
                    is VotingState.WaitingForVote -> {}
                    is VotingState.Results -> {
                        setVoteResultsReceived(votingState, imageLength)
                        setUpdateTimer(votingState, subscriptionCallback)
                    }
                    is VotingState.Update -> setVoteResultUpdate(votingState)
                }
            }, { throw (it) }, { subscriptionCallback(it) })

            viewModel.checkIfAlreadyVoted(optionViews.keys.toList())
        }
    }

    private fun setUpdateTimer(votingState: VotingState.Results, subscriptionCallback: (Subscription) -> Unit) {
        timer = fixedRateTimer(period = UPDATE_INTERVAL, initialDelay = UPDATE_INTERVAL) {
            viewModelBinding?.viewModel?.checkForUpdate(votingState.pollId, votingState.optionResults.results.keys.toList())
        }

        view.attachEvents().subscribe({
            when (it) {
                is ViewEvent.Attach -> {
                    // In case view has been detached for a while, don't want to wait 5 seconds to update
                    viewModelBinding?.viewModel?.checkForUpdate(votingState.pollId, votingState.optionResults.results.keys.toList())
                    log.d("poll view attached for poll ${votingState.pollId}")
                    timer = fixedRateTimer(period = UPDATE_INTERVAL, initialDelay = UPDATE_INTERVAL) {
                        viewModelBinding?.viewModel?.checkForUpdate(votingState.pollId, votingState.optionResults.results.keys.toList())
                    }
                }
                is ViewEvent.Detach -> {
                    log.d("poll view detached")
                    timer?.cancel()
                    timer?.purge()
                }
            }
        }, {}, { subscriptionCallback(it) })
    }

    private fun setVoteResultUpdate(votingUpdate: VotingState.Update) {
        votingUpdate.optionResults.results.forEach { (id, votingShare) ->
            val option = optionViews[id]

            viewModelBinding?.viewModel?.let {
                option?.updateResults(votingShare)
            }
        }
    }

    private fun setVoteResultsReceived(votingResults: VotingState.Results, viewWidth: Int) {
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
        return imagePoll.options.associate {
            it.id to view.imageOptionView {
                initializeOptionViewLayout(it, imageLength + OPTION_TEXT_HEIGHT.toInt())
                bindOptionView(it)
                bindOptionImageSize(imageLength)
                setupLayoutParams(
                    width = imageLength,
                    height = imageLength + OPTION_TEXT_HEIGHT.dpAsPx(view.resources.displayMetrics),
                    leftMargin = it.leftMargin.dpAsPx(view.resources.displayMetrics),
                    topMargin = it.topMargin.dpAsPx(view.resources.displayMetrics)
                )
            }
        }
    }
}

internal interface ViewImagePollInterface : MeasuredBindableView<ImagePollViewModelInterface>