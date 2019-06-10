package io.rover.sdk.ui.blocks.poll

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.support.v4.view.ViewCompat
import android.support.v7.widget.AppCompatTextView
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import io.rover.sdk.data.domain.FontWeight
import io.rover.sdk.data.domain.QuestionStyle
import io.rover.sdk.data.domain.TextPollBlock
import io.rover.sdk.data.domain.TextPollBlockOptionStyle
import io.rover.sdk.platform.addView
import io.rover.sdk.platform.create
import io.rover.sdk.platform.mapToFont
import io.rover.sdk.platform.optionView
import io.rover.sdk.platform.setBackgroundWithoutPaddingChange
import io.rover.sdk.platform.setupLayoutParams
import io.rover.sdk.platform.setupRelativeLayoutParams
import io.rover.sdk.platform.textView
import io.rover.sdk.streams.androidLifecycleDispose
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.concerns.background.BackgroundColorDrawableWrapper
import io.rover.sdk.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.ui.blocks.concerns.background.createBackgroundDrawable
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.MeasuredSize
import io.rover.sdk.ui.concerns.ViewModelBinding
import io.rover.sdk.ui.dpAsPx

internal class ViewTextPoll(override val view: LinearLayout) : ViewTextPollInterface {

    companion object {
        private const val MAX_OPTIONS_AMOUNT = 4
    }

    private val optionIds = (0 until MAX_OPTIONS_AMOUNT).map { ViewCompat.generateViewId() }

    override var viewModelBinding: MeasuredBindableView.Binding<TextPollViewModelInterface>? by ViewModelBinding { binding, _ ->
        binding?.viewModel?.let { viewModel ->
            setupQuestionView(viewModel)
            setupOptionViews(viewModel)
            informOptionBackgroundAboutSize(viewModel)

            viewModel.votingState.androidLifecycleDispose(view).subscribe { votingState ->
                when (votingState) {
                    is VotingState.WaitingForVote -> { }
                    is VotingState.Results -> setVoteResultsReceived(votingState)
                }
            }
        }
    }

    private fun setupQuestionView(viewModel: TextPollViewModelInterface) {
        view.addView(createQuestion(viewModel.textPollBlock))
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
            val option = view.findViewById<OptionView>(optionIds[index])
            val isSelectedOption = index == votingResults.selectedOption
            viewModelBinding?.viewModel?.let {
                option.goToResultsState(votingShare, isSelectedOption, it.textPollBlock.optionStyle)
            }
        }
    }

    private fun startListeningForOptionImageUpdates(
        viewModel: BackgroundViewModelInterface, optionViews: List<OptionView>
    ) {
        viewModel.backgroundUpdates.androidLifecycleDispose(view)
            .subscribe { (bitmap, fadeIn, backgroundImageConfiguration) ->
                val backgroundDrawable = bitmap.createBackgroundDrawable(view, viewModel.backgroundColor, fadeIn, backgroundImageConfiguration)
                optionViews.forEach { it.backgroundImage = backgroundDrawable }
            }
    }

    private fun createQuestion(textPollBlock: TextPollBlock): AppCompatTextView {
        return view.textView(textPollBlock.question) {
            setTextStyleProperties(textPollBlock.questionStyle)
            setupLayoutParams(width = ViewGroup.LayoutParams.MATCH_PARENT, height = ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun createOptionViews(textPollBlock: TextPollBlock): List<OptionView> {
        val optionStyle = textPollBlock.optionStyle
        val optionStyleHeight = optionStyle.height.dpAsPx(view.resources.displayMetrics)
        val optionMarginHeight = optionStyle.verticalSpacing.dpAsPx(view.resources.displayMetrics)
        val borderWidth = optionStyle.borderWidth.dpAsPx(view.resources.displayMetrics)

        return textPollBlock.options.mapIndexed { index, option ->
            view.optionView {
                id = optionIds[index]
                gravity = Gravity.CENTER_VERTICAL
                alpha = optionStyle.opacity.toFloat()
                setBackgroundColor(Color.TRANSPARENT)
                setupLayoutParams(
                    width = ViewGroup.LayoutParams.MATCH_PARENT,
                    height = optionStyleHeight + (borderWidth * 2),
                    topMargin = optionMarginHeight,
                    leftPadding = borderWidth,
                    rightPadding = borderWidth
                )
                createViews(option, textPollBlock.optionStyle)
            }
        }
    }

    private fun AppCompatTextView.setTextStyleProperties(questionStyle: QuestionStyle) {
        gravity = questionStyle.textAlignment.convertToGravity()
        textSize = questionStyle.font.size.toFloat()
        setTextColor(questionStyle.color.asAndroidColor())
        val font = questionStyle.font.weight.mapToFont()
        typeface = Typeface.create(font.fontFamily, font.fontStyle)
    }
}

internal interface ViewTextPollInterface : MeasuredBindableView<TextPollViewModelInterface>
