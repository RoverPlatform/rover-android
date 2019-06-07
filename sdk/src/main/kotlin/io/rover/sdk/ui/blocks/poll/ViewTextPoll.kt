package io.rover.sdk.ui.blocks.poll

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.support.v4.view.ViewCompat
import android.support.v7.widget.AppCompatTextView
import android.text.Layout
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import io.rover.sdk.data.domain.FontWeight
import io.rover.sdk.data.domain.HorizontalAlignment
import io.rover.sdk.data.domain.QuestionStyle
import io.rover.sdk.data.domain.TextAlignment
import io.rover.sdk.data.domain.TextPollBlock
import io.rover.sdk.data.domain.TextPollBlockOptionStyle
import io.rover.sdk.platform.button
import io.rover.sdk.platform.mapToFont
import io.rover.sdk.platform.optionView
import io.rover.sdk.platform.setDimens
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
import io.rover.sdk.ui.pxAsDp

internal class ViewTextPoll(override val view: LinearLayout) : ViewTextPollInterface {

    private val optionIDs = listOf(ViewCompat.generateViewId(), ViewCompat.generateViewId(), ViewCompat.generateViewId(), ViewCompat.generateViewId())

    override var viewModelBinding: MeasuredBindableView.Binding<TextPollViewModelInterface>? by ViewModelBinding { binding, _ ->
        binding?.viewModel?.let {

            val optionStyleHeight = it.textPollBlock.optionStyle.height
            val borderWidth = it.textPollBlock.optionStyle.borderWidth

            val optionViews = createOptionViews(it.textPollBlock)

            startListeningForOptionImageUpdates(it.optionBackgroundViewModel, optionViews)

            view.addView(createQuestion(it.textPollBlock))
            optionViews.forEachIndexed { index, optionView ->
                view.addView(optionView)
                optionView.setOnClickListener {
                    setOptionClicked(index)
                }
            }

            binding.measuredSize?.width?.let { measuredWidth ->
                val width = measuredWidth - (borderWidth * 2)
                val height = optionStyleHeight + (borderWidth * 2).toFloat()

                val optionHeight = MeasuredSize(
                    width = width,
                    height = height,
                    density = view.resources.displayMetrics.density
                )

                it.optionBackgroundViewModel.informDimensions(optionHeight)
            }

            it.votingState.androidLifecycleDispose(view).subscribe { votingState ->
                when(votingState) {
                    is VotingState.WaitingForVote -> {}
                    is VotingState.Results -> {
                        setVoteResultsReceived(votingState)
                    }
                }
            }
        }
    }

    private fun setVoteResultsReceived(votingResults: VotingState.Results) {
        votingResults.votingShare.forEachIndexed { index, votingShare ->
            val option = view.findViewById<OptionView>(optionIDs[index])
            option.goToResultsState(votingShare, index == votingResults.selectedOption, viewModelBinding!!.viewModel.textPollBlock.optionStyle)
        }
    }

    private fun setOptionClicked(selectedOption: Int) {
        viewModelBinding?.viewModel?.castVote(selectedOption)
    }

    private fun startListeningForOptionImageUpdates(
        it: BackgroundViewModelInterface,
        optionViews: List<OptionView>
    ) {
        it.backgroundUpdates.androidLifecycleDispose(view)
            .subscribe { (bitmap, fadeIn, backgroundImageConfiguration) ->
                val backgroundDrawable = bitmap.createBackgroundDrawable(
                    view,
                    it.backgroundColor,
                    fadeIn,
                    backgroundImageConfiguration
                )

                optionViews.forEach {
                    it.backgroundImage = backgroundDrawable
                    val paddingLeft = it.paddingLeft
                    val paddingTop = it.paddingTop
                    val paddingRight = it.paddingRight
                    val paddingBottom = it.paddingBottom
                    it.background = backgroundDrawable
                    it.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
                }
            }
    }

    private fun createQuestion(textPollBlock: TextPollBlock): AppCompatTextView {
        return view.textView {
            text = textPollBlock.question
            setTextStyleProperties(textPollBlock.questionStyle)
            layoutParams = ViewGroup.MarginLayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun createOptionViews(textPollBlock: TextPollBlock): List<OptionView> {
        val optionStyle = textPollBlock.optionStyle
        val optionStyleHeight = optionStyle.height.dpAsPx(view.resources.displayMetrics)
        val optionMarginHeight = optionStyle.verticalSpacing.dpAsPx(view.resources.displayMetrics)
        val borderWidth = optionStyle.borderWidth.dpAsPx(view.resources.displayMetrics)

        return textPollBlock.options.mapIndexed { index, option ->
            view.optionView {
                gravity = Gravity.CENTER_VERTICAL
                id = optionIDs[index]
                alpha = optionStyle.opacity.toFloat()
                setBackgroundColor(Color.TRANSPARENT)
                setDimens(
                    width = LinearLayout.LayoutParams.MATCH_PARENT,
                    height = optionStyleHeight + (borderWidth * 2),
                    topMargin = optionMarginHeight,
                    horizontalPadding = borderWidth
                )
                createViews(option, textPollBlock.optionStyle)
                setResultColor(optionStyle.resultFillColor.asAndroidColor())
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

    fun createViews(option: String, optionStyle: TextPollBlockOptionStyle) {
        val answerOption = textView {
            setSingleLine(true)
            this.id = textId
            ellipsize = TextUtils.TruncateAt.END
            text = option
            textSize = optionStyle.font.size.toFloat()
            setTextColor(optionStyle.color.asAndroidColor())
            val font = optionStyle.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)

            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                val marginInPixels = 16.dpAsPx(resources.displayMetrics)
                setMargins(marginInPixels, 0, 0, 0)
                addRule(ALIGN_PARENT_START)
            }
        }

       addView(answerOption)

        borderRadius = optionStyle.borderRadius.dpAsPx(resources.displayMetrics).toFloat()
        strokeWidthX = optionStyle.borderWidth.dpAsPx(resources.displayMetrics).toFloat()

        paint = Paint().apply {
            color = optionStyle.borderColor.asAndroidColor()
            this.strokeWidth = strokeWidthX
            style = Paint.Style.STROKE
        }

        fillColorPaint = Paint().apply {
            color = optionStyle.background.color.asAndroidColor()
            style = Paint.Style.FILL
        }
    }

    private var strokeWidthX = 0f
    private var borderRadius = 0f
    private var paint = Paint()
    private var fillColorPaint = Paint()
    private var resultColor = 0
    var backgroundImage: BackgroundColorDrawableWrapper? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        //TODO: Extract this from on draw - Churning == bad
        val halfStrokeWidth = strokeWidthX / 2

        val rectWithBorders = android.graphics.RectF(
            halfStrokeWidth,
            halfStrokeWidth,
            width.toFloat() - halfStrokeWidth,
            height.toFloat() - halfStrokeWidth
        )

        //TODO: Clip to avoid overdraw

        if (backgroundImage == null) {
            canvas.drawRoundRect(rectWithBorders, borderRadius, borderRadius, fillColorPaint)
        }

        if (strokeWidthX != 0f) {
            canvas.drawRoundRect(rectWithBorders, borderRadius, borderRadius, paint)
        }
    }

    fun setResultColor(resultColor: Int) {
        this.resultColor = resultColor
    }

    private fun FontWeight.getIncreasedFontWeight(): FontWeight {
        return FontWeight.values().getOrElse(this.ordinal + 2) { FontWeight.Black }
    }

    fun goToResultsState(votingShare: Int, voted: Boolean, optionStyle: TextPollBlockOptionStyle) {
        val votePercentageText = textView {
            this.id = ViewCompat.generateViewId()
            text = "$votingShare%"
            textSize = (optionStyle.font.size * 1.05).toFloat()
            setTextColor(optionStyle.color.asAndroidColor())

            val font = optionStyle.font.weight.getIncreasedFontWeight().mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                val marginInDp = 16.dpAsPx(resources.displayMetrics)
                setMargins(marginInDp, 0, marginInDp, 0)
                addRule(ALIGN_PARENT_RIGHT)
            }
            textAlignment = View.TEXT_ALIGNMENT_TEXT_END

        }

        addView(votePercentageText)

        val voteIndicatorView = textView {
            this.id = ViewCompat.generateViewId()
            text = "\u00B7"
            textSize = (optionStyle.font.size * 1.05).toFloat()
            setTextColor(optionStyle.color.asAndroidColor())
            val font = optionStyle.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
            textAlignment = View.TEXT_ALIGNMENT_TEXT_START
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                val marginInPixels = 8.dpAsPx(resources.displayMetrics)
                setMargins(marginInPixels, 0, 0, 0)
                addRule(END_OF, textId)
            }
        }

        if (voted) addView(voteIndicatorView)

        val textView = findViewById<AppCompatTextView>(textId)
        textView.apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                val marginInPixels = 16.dpAsPx(resources.displayMetrics)
                setMargins(marginInPixels, 0, 0, 0)
                addRule(ALIGN_PARENT_LEFT)
            }
        }

        findViewById<AppCompatTextView>(textId).apply {
            voteIndicatorView.measure(0,0)
            votePercentageText.measure(0, 0)
            val voteIndicatorWidth = if (voted) voteIndicatorView.measuredWidth + (voteIndicatorView.layoutParams as MarginLayoutParams).marginStart else 0
            val votePercentageMargins = (votePercentageText.layoutParams as MarginLayoutParams).marginEnd + (votePercentageText.layoutParams as MarginLayoutParams).marginStart
            val votePercentageWidth = votePercentageText.measuredWidth + votePercentageMargins
            val borderWidth = (optionStyle.borderWidth.dpAsPx(resources.displayMetrics) * 2)

            val widthOfOtherViews = voteIndicatorWidth + (layoutParams as MarginLayoutParams).marginStart + votePercentageWidth + borderWidth
            maxWidth = this@OptionView.width - widthOfOtherViews
        }
    }
}

internal interface ViewTextPollInterface : MeasuredBindableView<TextPollViewModelInterface>