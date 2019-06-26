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
import io.rover.sdk.data.mapToFont
import io.rover.sdk.platform.create
import io.rover.sdk.platform.setBackgroundWithoutPaddingChange
import io.rover.sdk.platform.setupLayoutParams
import io.rover.sdk.platform.setupRelativeLayoutParams
import io.rover.sdk.platform.textView
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.concerns.background.BackgroundColorDrawableWrapper
import io.rover.sdk.ui.dpAsPx

/**
 * Custom view that is "externally bound", the "bind" methods on this class are externally called
 * as opposed to other views which subscribe to a ViewModel. This means that this view and the views
 * that interact with it operate in a more MVP type approach than the other MVVM-esque views.
 */
internal class TextOptionView(context: Context?) : RelativeLayout(context) {
    private val optionTextView = textView {
        id = ViewCompat.generateViewId()
        ellipsize = TextUtils.TruncateAt.END
        maxLines = 1
        gravity = Gravity.CENTER_VERTICAL
        val marginInPixels = 16.dpAsPx(resources.displayMetrics)

        setupRelativeLayoutParams(
            width = ViewGroup.LayoutParams.WRAP_CONTENT, height = ViewGroup.LayoutParams.MATCH_PARENT,
            leftMargin = marginInPixels, rightMargin = marginInPixels
        ) {
            addRule(ALIGN_PARENT_START)
        }
    }

    private val voteIndicatorView = textView {
        id = ViewCompat.generateViewId()
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
            addRule(END_OF, optionTextView.id)
        }
    }

    private val votePercentageText = textView {
        id = ViewCompat.generateViewId()
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

    /**
     * Setting the layer type to software disables hardware acceleration for this view. It is being disabled here due
     * to issues with wrongly rendered pixel and invisible elements when using hardware acceleration in conjunction
     * with custom views and drawing calls.
     * https://developer.android.com/guide/topics/graphics/hardware-accel
     */
    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        addView(optionTextView)
        addView(voteIndicatorView)
        addView(votePercentageText)
    }

    fun initializeOptionViewLayout(optionStyle: TextPollBlockOptionStyle) {
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

        initializeViewStyle(optionStyle)
    }

    fun bindOptionView(option: String, optionStyle: TextPollBlockOptionStyle) {
        optionTextView.run {
            text = option
            textSize = optionStyle.font.size.toFloat()
            setTextColor(optionStyle.color.asAndroidColor())
            val font = optionStyle.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }

    private fun initializeViewStyle(optionStyle: TextPollBlockOptionStyle) {
        val borderRadius = optionStyle.borderRadius.dpAsPx(resources.displayMetrics).toFloat()
        val borderStrokeWidth = optionStyle.borderWidth.dpAsPx(resources.displayMetrics).toFloat()
        val doubleBorderStrokeWidth = borderStrokeWidth * 2

        val borderPaint = Paint().create(
            optionStyle.borderColor.asAndroidColor(),
            Paint.Style.STROKE,
            doubleBorderStrokeWidth
        )
        val fillPaint = Paint().create(optionStyle.background.color.asAndroidColor(), Paint.Style.FILL)
        val resultPaint = Paint().create(optionStyle.resultFillColor.asAndroidColor(), Paint.Style.FILL)

        optionPaints = OptionPaints(borderPaint, fillPaint, resultPaint)

        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        roundRect = RoundRect(rect, borderRadius, doubleBorderStrokeWidth)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        roundRect = roundRect?.copy(rectF = RectF(0f, 0f, width.toFloat(), height.toFloat()))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        roundRect?.let { roundRect ->
            roundRect.rectF?.let {
                if (backgroundImage == null) canvas.drawRoundRect(
                    it,
                    roundRect.borderRadius,
                    roundRect.borderRadius,
                    optionPaints.fillPaint
                )
                if (inResultState) canvas.drawRoundRect(
                    it,
                    roundRect.borderRadius,
                    roundRect.borderRadius,
                    optionPaints.resultPaint
                )
                if (roundRect.borderStrokeWidth != 0f) canvas.drawRoundRect(
                    it,
                    roundRect.borderRadius,
                    roundRect.borderRadius,
                    optionPaints.borderPaint
                )
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
        votePercentageText.visibility = View.VISIBLE

        bindVoteIndicatorText(optionStyle)

        if (isSelectedOption) voteIndicatorView.visibility = View.VISIBLE

        optionTextView.run {
            gravity = Gravity.CENTER_VERTICAL
            val marginInPixels = 16.dpAsPx(resources.displayMetrics)

            setupRelativeLayoutParams(
                width = LayoutParams.WRAP_CONTENT,
                height = LayoutParams.MATCH_PARENT,
                leftMargin = marginInPixels
            ) {
                addRule(ALIGN_PARENT_LEFT)
            }

            val widthOfOtherViews =
                calculateWidthWithoutOptionText(voteIndicatorView, votePercentageText, isSelectedOption, optionStyle)
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
        val borderWidth = (optionStyle.borderWidth.dpAsPx(resources.displayMetrics))
        val optionEndMargin = (optionTextView.layoutParams as MarginLayoutParams).marginStart

        return voteIndicatorWidth + optionEndMargin + votePercentageWidth + borderWidth
    }

    private fun bindVotePercentageText(
        votingShare: Int,
        optionStyle: TextPollBlockOptionStyle
    ) {
        votePercentageText.run {
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
        voteIndicatorView.run {
            text = "\u2022"
            textSize = optionStyle.font.size.toFloat()
            setTextColor(optionStyle.color.asAndroidColor())
            val font = optionStyle.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }
}

private data class OptionPaints(
    val borderPaint: Paint = Paint(),
    val fillPaint: Paint = Paint(),
    val resultPaint: Paint = Paint()
)