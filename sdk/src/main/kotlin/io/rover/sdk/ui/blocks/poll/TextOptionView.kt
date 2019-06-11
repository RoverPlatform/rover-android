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
import android.widget.RelativeLayout
import io.rover.sdk.data.domain.FontWeight
import io.rover.sdk.data.domain.TextPollBlockOptionStyle
import io.rover.sdk.platform.addView
import io.rover.sdk.platform.create
import io.rover.sdk.platform.mapToFont
import io.rover.sdk.platform.setBackgroundWithoutPaddingChange
import io.rover.sdk.platform.setupLayoutParams
import io.rover.sdk.platform.setupRelativeLayoutParams
import io.rover.sdk.platform.textView
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.concerns.background.BackgroundColorDrawableWrapper
import io.rover.sdk.ui.dpAsPx

internal class TextOptionView(context: Context?) : RelativeLayout(context) {
    private val optionTextId = ViewCompat.generateViewId()
    private val voteIndicatorId = ViewCompat.generateViewId()
    private val votePercentageTextId = ViewCompat.generateViewId()

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

    companion object {
        private const val RESULTS_TEXT_SCALE_FACTOR = 1.05f
    }



    fun initializeOptionView(optionStyle: TextPollBlockOptionStyle) {
        val optionStyleHeight = optionStyle.height.dpAsPx(resources.displayMetrics)
        val optionMarginHeight = optionStyle.verticalSpacing.dpAsPx(resources.displayMetrics)
        val borderWidth = optionStyle.borderWidth.dpAsPx(resources.displayMetrics)

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
    }

    fun bindOptionView(option: String, optionStyle: TextPollBlockOptionStyle) {
        findViewById<AppCompatTextView>(optionTextId).run {
            text = option
            textSize = optionStyle.font.size.toFloat()
            setTextColor(optionStyle.color.asAndroidColor())
            val font = optionStyle.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
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

    fun goToResultsState(votingShare: Int, isSelectedOption: Boolean, optionStyle: TextPollBlockOptionStyle) {
        if (inResultState) return
        inResultState = true

        bindVotePercentageText(votingShare, optionStyle)
        val votePercentageText = findViewById<AppCompatTextView>(votePercentageTextId)
        votePercentageText.visibility = View.VISIBLE

        bindVoteIndicatorText(optionStyle)
        val voteIndicator = findViewById<AppCompatTextView>(voteIndicatorId)
        if (isSelectedOption) voteIndicator.visibility = View.VISIBLE

        val optionTextView = findViewById<AppCompatTextView>(optionTextId)
        optionTextView.run {
            gravity = Gravity.CENTER_VERTICAL
            val marginInPixels = 16.dpAsPx(resources.displayMetrics)

            setupRelativeLayoutParams(
                width = LayoutParams.WRAP_CONTENT,
                height = LayoutParams.MATCH_PARENT,
                leftMargin = marginInPixels) {
                addRule(ALIGN_PARENT_LEFT)
            }

            val widthOfOtherViews = calculateWidthWithoutOptionText(voteIndicator, votePercentageText, isSelectedOption, optionStyle)
            maxWidth = this@TextOptionView.width - widthOfOtherViews
        }
    }

    private fun calculateWidthWithoutOptionText(
        voteIndicatorView: AppCompatTextView,
        votePercentageText: AppCompatTextView,
        isSelectedOption: Boolean,
        optionStyle: TextPollBlockOptionStyle
    ): Int {
        voteIndicatorView.measure(0, 0)
        votePercentageText.measure(0, 0)
        val voteIndicatorWidth =
            if (isSelectedOption) voteIndicatorView.measuredWidth + (voteIndicatorView.layoutParams as MarginLayoutParams).marginStart else 0
        val votePercentageMargins =
            (votePercentageText.layoutParams as MarginLayoutParams).marginEnd + (votePercentageText.layoutParams as MarginLayoutParams).marginStart
        val votePercentageWidth = votePercentageText.measuredWidth + votePercentageMargins
        val borderWidth = (optionStyle.borderWidth.dpAsPx(resources.displayMetrics) * 2)

        return voteIndicatorWidth + (layoutParams as MarginLayoutParams).marginStart + votePercentageWidth + borderWidth
    }

    init {
        addView {
            textView {
                id = optionTextId
                ellipsize = TextUtils.TruncateAt.END
                maxLines = 1
                gravity = Gravity.CENTER_VERTICAL
                val marginInPixels = 16.dpAsPx(resources.displayMetrics)

                setupRelativeLayoutParams(width = ViewGroup.LayoutParams.WRAP_CONTENT, height = ViewGroup.LayoutParams.MATCH_PARENT,
                    leftMargin = marginInPixels, rightMargin = marginInPixels) {
                    addRule(ALIGN_PARENT_START)
                }

            }
        }

        addView {
            textView {
                id = voteIndicatorId
                visibility = View.GONE
                maxLines = 1
                gravity = Gravity.CENTER_VERTICAL
                textAlignment = View.TEXT_ALIGNMENT_TEXT_START

                val marginInPixels = 8.dpAsPx(resources.displayMetrics)

                setupRelativeLayoutParams(
                    width = LayoutParams.WRAP_CONTENT,
                    height = LayoutParams.MATCH_PARENT,
                    leftMargin = marginInPixels
                ) {
                    addRule(END_OF, optionTextId)
                }
            }
        }

        addView {
            textView {
                id = votePercentageTextId
                visibility = View.GONE
                maxLines = 1
                gravity = Gravity.CENTER_VERTICAL
                textAlignment = View.TEXT_ALIGNMENT_TEXT_END

                val marginInDp = 16.dpAsPx(resources.displayMetrics)

                setupRelativeLayoutParams(
                    width = LayoutParams.WRAP_CONTENT, height = LayoutParams.MATCH_PARENT,
                    leftMargin = marginInDp, rightMargin = marginInDp
                ) {
                    addRule(ALIGN_PARENT_RIGHT)
                }
            }
        }
    }

    private fun bindVotePercentageText(
        votingShare: Int,
        optionStyle: TextPollBlockOptionStyle
    ) {
        findViewById<AppCompatTextView>(votePercentageTextId).run {
            textSize = (optionStyle.font.size * RESULTS_TEXT_SCALE_FACTOR)
            setTextColor(optionStyle.color.asAndroidColor())
            text = "$votingShare%"
            val font = optionStyle.font.weight.getIncreasedFontWeight().mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }

    private fun FontWeight.getIncreasedFontWeight(): FontWeight {
        return FontWeight.values().getOrElse(this.ordinal + 2) { FontWeight.Black }
    }

    private fun bindVoteIndicatorText(optionStyle: TextPollBlockOptionStyle) {
        findViewById<AppCompatTextView>(voteIndicatorId).run{
            text = "\u2022"
            textSize = (optionStyle.font.size * RESULTS_TEXT_SCALE_FACTOR)
            setTextColor(optionStyle.color.asAndroidColor())
            val font = optionStyle.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }
}

private data class RoundRect(val rectF: RectF?, val borderRadius: Float, val halfBorderStrokeWidth: Float)
private data class OptionPaints(val borderPaint: Paint = Paint(), val fillPaint: Paint = Paint(), val resultPaint: Paint = Paint())
