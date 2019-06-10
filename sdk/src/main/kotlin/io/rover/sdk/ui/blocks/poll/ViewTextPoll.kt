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
            option.goToResultsState(votingShare, isSelectedOption, viewModelBinding!!.viewModel.textPollBlock.optionStyle)
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

internal class OptionView(context: Context?) : RelativeLayout(context) {

    private val textId = ViewCompat.generateViewId()

    private var roundRect: RoundRect? = null
    private var optionPaints: OptionPaints = OptionPaints()
    private var inResultState = false

    var backgroundImage: BackgroundColorDrawableWrapper? = null
        set(value) {
            field = value
            value?.let {
                setBackgroundWithoutPaddingChange(it)
            }
        }

    fun createViews(option: String, optionStyle: TextPollBlockOptionStyle) {
        addView {
            textView(option) {
                id = textId
                ellipsize = TextUtils.TruncateAt.END
                maxLines = 1
                gravity = Gravity.CENTER_VERTICAL
                textSize = optionStyle.font.size.toFloat()
                setTextColor(optionStyle.color.asAndroidColor())
                val font = optionStyle.font.weight.mapToFont()
                typeface = Typeface.create(font.fontFamily, font.fontStyle)
                val marginInPixels = 16.dpAsPx(resources.displayMetrics)

                setupRelativeLayoutParams(width = ViewGroup.LayoutParams.WRAP_CONTENT, height = ViewGroup.LayoutParams.MATCH_PARENT,
                    leftMargin = marginInPixels, rightMargin = marginInPixels) {
                    addRule(ALIGN_PARENT_START)
                }
            }
        }

        initializeViewStyle(optionStyle)
    }

    private fun initializeViewStyle(optionStyle: TextPollBlockOptionStyle) {
        val borderRadius = optionStyle.borderRadius.dpAsPx(resources.displayMetrics).toFloat()
        val borderStrokeWidth = optionStyle.borderWidth.dpAsPx(resources.displayMetrics).toFloat()

        val borderPaint = Paint().create(
            optionStyle.borderColor.asAndroidColor(),
            Paint.Style.STROKE,
            borderStrokeWidth
        )
        val fillPaint = Paint().create(optionStyle.background.color.asAndroidColor(), Paint.Style.FILL)
        val resultPaint = Paint().create(optionStyle.resultFillColor.asAndroidColor(), Paint.Style.FILL)

        optionPaints = OptionPaints(borderPaint, fillPaint, resultPaint)

        val halfStrokeWidth = borderStrokeWidth / 2
        val rect = RectF(
            halfStrokeWidth,
            halfStrokeWidth,
            width.toFloat() - (halfStrokeWidth),
            height.toFloat() - halfStrokeWidth
        )
        roundRect = RoundRect(rect, borderRadius, halfStrokeWidth)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val halfStrokeWidth = roundRect?.halfBorderStrokeWidth ?: 0f
        roundRect = roundRect?.copy(rectF = RectF(halfStrokeWidth, halfStrokeWidth, width.toFloat() - (halfStrokeWidth), height.toFloat() - halfStrokeWidth))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        roundRect?.let { roundRect ->
            roundRect.rectF.let {
                if (backgroundImage == null) canvas.drawRoundRect(it, roundRect.borderRadius, roundRect.borderRadius, optionPaints.fillPaint)
                if (roundRect.halfBorderStrokeWidth != 0f) canvas.drawRoundRect(it, roundRect.borderRadius, roundRect.borderRadius, optionPaints.borderPaint)
                if (inResultState) canvas.drawRoundRect(it, roundRect.borderRadius, roundRect.borderRadius, optionPaints.resultPaint)
            }
        }
    }

    override fun draw(canvas: Canvas?) {
        roundRect?.let { roundRect ->
            roundRect.rectF.let {
                canvas?.clipPath(Path().apply {
                    addRoundRect(it, roundRect.borderRadius, roundRect.borderRadius, Path.Direction.CW)
                })
            }
        }
        super.draw(canvas)
    }

    private fun FontWeight.getIncreasedFontWeight(): FontWeight {
        return FontWeight.values().getOrElse(this.ordinal + 2) { FontWeight.Black }
    }

    fun goToResultsState(votingShare: Int, voted: Boolean, optionStyle: TextPollBlockOptionStyle) {
        if (inResultState) return
        inResultState = true

        val votePercentageText = textView("$votingShare%") {
            this.id = ViewCompat.generateViewId()
            maxLines = 1
            gravity = Gravity.CENTER_VERTICAL
            textSize = (optionStyle.font.size * 1.05).toFloat()
            setTextColor(optionStyle.color.asAndroidColor())

            val font = optionStyle.font.weight.getIncreasedFontWeight().mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)

            val marginInDp = 16.dpAsPx(resources.displayMetrics)

            setupRelativeLayoutParams(width = LayoutParams.WRAP_CONTENT, height = LayoutParams.MATCH_PARENT,
                leftMargin = marginInDp, rightMargin = marginInDp) {
                addRule(ALIGN_PARENT_RIGHT)
            }

            textAlignment = View.TEXT_ALIGNMENT_TEXT_END
        }

        addView(votePercentageText)

        val voteIndicatorView = textView("\u2022") {
            id = ViewCompat.generateViewId()
            maxLines = 1
            textSize = (optionStyle.font.size * 1.05).toFloat()
            setTextColor(optionStyle.color.asAndroidColor())
            val font = optionStyle.font.weight.mapToFont()
            gravity = Gravity.CENTER_VERTICAL
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
            textAlignment = View.TEXT_ALIGNMENT_TEXT_START
            val marginInPixels = 8.dpAsPx(resources.displayMetrics)

            setupRelativeLayoutParams(width = LayoutParams.WRAP_CONTENT, height = LayoutParams.MATCH_PARENT, leftMargin = marginInPixels) {
                addRule(END_OF, textId)
            }
        }

        if (voted) addView(voteIndicatorView)

        val textView = findViewById<AppCompatTextView>(textId)
        textView.run {
            gravity = Gravity.CENTER_VERTICAL
            val marginInPixels = 16.dpAsPx(resources.displayMetrics)

            setupRelativeLayoutParams(width = LayoutParams.WRAP_CONTENT, height = LayoutParams.MATCH_PARENT,
                leftMargin = marginInPixels) {
                addRule(ALIGN_PARENT_LEFT)
            }

            voteIndicatorView.measure(0, 0)
            votePercentageText.measure(0, 0)
            val voteIndicatorWidth =
                if (voted) voteIndicatorView.measuredWidth + (voteIndicatorView.layoutParams as MarginLayoutParams).marginStart else 0
            val votePercentageMargins =
                (votePercentageText.layoutParams as MarginLayoutParams).marginEnd + (votePercentageText.layoutParams as MarginLayoutParams).marginStart
            val votePercentageWidth = votePercentageText.measuredWidth + votePercentageMargins
            val borderWidth = (optionStyle.borderWidth.dpAsPx(resources.displayMetrics) * 2)

            val widthOfOtherViews = voteIndicatorWidth + (layoutParams as MarginLayoutParams).marginStart + votePercentageWidth + borderWidth
            maxWidth = this@OptionView.width - widthOfOtherViews
        }
    }
}

internal interface ViewTextPollInterface : MeasuredBindableView<TextPollViewModelInterface>

private data class RoundRect(val rectF: RectF?, val borderRadius: Float, val halfBorderStrokeWidth: Float)
private data class OptionPaints(val borderPaint: Paint = Paint(), val fillPaint: Paint = Paint(), val resultPaint: Paint = Paint())