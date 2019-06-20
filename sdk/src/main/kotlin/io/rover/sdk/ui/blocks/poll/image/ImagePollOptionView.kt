package io.rover.sdk.ui.blocks.poll.image

import android.content.Context
import android.graphics.Bitmap
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM
import android.widget.RelativeLayout.CENTER_HORIZONTAL
import io.rover.sdk.data.domain.FontWeight
import io.rover.sdk.data.domain.ImagePollBlockOptionStyle
import io.rover.sdk.data.mapToFont
import io.rover.sdk.platform.create
import io.rover.sdk.platform.imageView
import io.rover.sdk.platform.linearLayout
import io.rover.sdk.platform.relativeLayout
import io.rover.sdk.platform.setupLinearLayoutParams
import io.rover.sdk.platform.setupRelativeLayoutParams
import io.rover.sdk.platform.textView
import io.rover.sdk.platform.votingIndicatorBar
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.poll.RoundRect
import io.rover.sdk.ui.dpAsPx

/**
 * Custom view that is "externally bound", the "bind" methods on this class are externally called
 * as opposed to other views which subscribe to a ViewModel. This means that this view and the views
 * that interact with it operate in a more MVP type approach than the other MVVM-esque views.
 */
internal class ImagePollOptionView(context: Context?) : LinearLayout(context) {
    private val bottomSectionHeight = 40f.dpAsPx(this.resources.displayMetrics)
    private val bottomSectionHorizontalMargin = 8.dpAsPx(resources.displayMetrics)
    private val voteIndicatorViewLeftMargin = 8.dpAsPx(resources.displayMetrics)
    private val votePercentageTextBottomMargin = 8.dpAsPx(resources.displayMetrics)
    private val votePercentageViewTextSize = 16f
    private val votingIndicatorBarHeight = 8f.dpAsPx(this.resources.displayMetrics)
    private val votingIndicatorBarBottomMargin = 8f.dpAsPx(this.resources.displayMetrics)

    private val optionTextView = textView {
        id = ViewCompat.generateViewId()
        ellipsize = TextUtils.TruncateAt.END
        maxLines = 1
        gravity = Gravity.CENTER_VERTICAL
        setupLinearLayoutParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = bottomSectionHeight)
    }

    private val votingIndicatorBar = votingIndicatorBar {
        id = ViewCompat.generateViewId()
        visibility = View.GONE
    }

    private val votePercentageView = textView {
        id = ViewCompat.generateViewId()
        visibility = View.GONE
        maxLines = 1
        gravity = Gravity.CENTER_VERTICAL
        textAlignment = View.TEXT_ALIGNMENT_TEXT_END
    }

    private val topSection = RelativeLayout(context)

    private val bottomSection = relativeLayout {
        gravity = Gravity.CENTER
        setupLinearLayoutParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = bottomSectionHeight
        )
    }

    private val bottomSectionLinear = linearLayout {
        gravity = Gravity.CENTER
        setupRelativeLayoutParams(
            width = ViewGroup.LayoutParams.WRAP_CONTENT,
            height = bottomSectionHeight
        )
    }

    private val voteIndicatorView: AppCompatTextView = textView {
        id = ViewCompat.generateViewId()
        visibility = View.GONE
        maxLines = 1
        gravity = Gravity.CENTER

        setupLinearLayoutParams(
            width = LayoutParams.WRAP_CONTENT,
            height = bottomSectionHeight,
            leftMargin = voteIndicatorViewLeftMargin
        )
    }

    private val optionImageView = imageView {}
    private var roundRect: RoundRect? = null
    private var borderPaint = Paint()

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        orientation = VERTICAL
        topSection.addView(optionImageView)
        topSection.addView(votePercentageView)
        topSection.addView(votingIndicatorBar)
        bottomSectionLinear.addView(optionTextView)
        bottomSectionLinear.addView(voteIndicatorView)
        bottomSection.addView(bottomSectionLinear)
        addView(topSection)
        addView(bottomSection)
    }

    fun bindOptionView(option: String, optionStyle: ImagePollBlockOptionStyle) {
        val borderWidth = optionStyle.border.width.dpAsPx(this.resources.displayMetrics)
        bottomSection.layoutParams = (bottomSection.layoutParams as MarginLayoutParams).apply {
            setMargins(borderWidth + bottomSectionHorizontalMargin, 0, borderWidth + bottomSectionHorizontalMargin, borderWidth * 2)
        }

        optionTextView.run {
            text = option
            textSize = optionStyle.font.size.toFloat()
            setTextColor(optionStyle.color.asAndroidColor())
            val font = optionStyle.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }

    fun bindOptionImageSize(imageLength: Int) {
        topSection.layoutParams = LayoutParams(imageLength, imageLength)
        optionImageView.run {
            scaleType = ImageView.ScaleType.FIT_XY
            layoutParams = RelativeLayout.LayoutParams(imageLength, imageLength)
        }
    }

    fun bindOptionImage(bitmap: Bitmap) {
        optionImageView.run {
            setImageBitmap(bitmap)
        }
    }

    fun goToResultsState(votingShare: Int, isSelectedOption: Boolean, optionStyle: ImagePollBlockOptionStyle) {
        bindVoteIndicatorBar(optionStyle)
        bindVotePercentageText(votingShare)
        bindVoteIndicatorText(optionStyle)
        if (isSelectedOption) voteIndicatorView.visibility = View.VISIBLE

        optionTextView.run {
            gravity = Gravity.CENTER_VERTICAL
            setupLinearLayoutParams(
                width = RelativeLayout.LayoutParams.WRAP_CONTENT,
                height = RelativeLayout.LayoutParams.MATCH_PARENT)

            if (isSelectedOption) {
                voteIndicatorView.measure(0, 0)
                maxWidth = bottomSection.width - voteIndicatorView.measuredWidth + (voteIndicatorView.layoutParams as MarginLayoutParams).marginStart
            }
        }
    }

    private fun bindVotePercentageText(votingShare: Int) {
        votePercentageView.run {
            textSize = votePercentageViewTextSize
            setTextColor(Color.WHITE)
            text = "$votingShare%"
            val font = FontWeight.Medium.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
            visibility = View.VISIBLE
            setupRelativeLayoutParams(
                width = RelativeLayout.LayoutParams.WRAP_CONTENT, height = RelativeLayout.LayoutParams.WRAP_CONTENT,
                bottomMargin = votePercentageTextBottomMargin
            ) {
                addRule(RelativeLayout.ABOVE, votingIndicatorBar.id)
                addRule(CENTER_HORIZONTAL)
            }
        }
    }

    private fun bindVoteIndicatorBar(optionStyle: ImagePollBlockOptionStyle) {
        votingIndicatorBar.run {
            visibility = View.VISIBLE
            setupRelativeLayoutParams(
                width = RelativeLayout.LayoutParams.MATCH_PARENT,
                height = votingIndicatorBarHeight,
                bottomMargin = votingIndicatorBarBottomMargin,
                leftMargin = optionStyle.border.width.dpAsPx(this.resources.displayMetrics),
                rightMargin = optionStyle.border.width.dpAsPx(this.resources.displayMetrics)) {
                addRule(ALIGN_PARENT_BOTTOM)
            }
        }
    }

    private fun bindVoteIndicatorText(optionStyle: ImagePollBlockOptionStyle) {
        voteIndicatorView.run {
            text = "\u2022"
            textSize = optionStyle.font.size.toFloat()
            setTextColor(optionStyle.color.asAndroidColor())
            val font = optionStyle.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }

    fun initializeOptionViewLayout(optionStyle: ImagePollBlockOptionStyle) {
        gravity = Gravity.CENTER_VERTICAL
        alpha = optionStyle.opacity.toFloat()
        setBackgroundColor(Color.TRANSPARENT)

        val borderRadius = optionStyle.border.radius.dpAsPx(resources.displayMetrics).toFloat()
        val borderStrokeWidth = optionStyle.border.width.dpAsPx(resources.displayMetrics).toFloat()

        borderPaint = Paint().create(
            optionStyle.border.color.asAndroidColor(),
            Paint.Style.STROKE,
            borderStrokeWidth * 2
        )

        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        roundRect = RoundRect(rect, borderRadius, borderStrokeWidth)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        roundRect = roundRect?.copy(
            rectF = RectF(
                0f,
                0f,
                width.toFloat(),
                height.toFloat()
            )
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        roundRect?.let { roundRect ->
            roundRect.rectF.let {
                if (roundRect.borderStrokeWidth != 0f) {
                    canvas.drawRoundRect(
                        it,
                        roundRect.borderRadius,
                        roundRect.borderRadius,
                        borderPaint)
                }
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
}

class VotingIndicatorBar(context: Context?): View(context) {

    private val borderPaint = Paint().create(Color.RED, Paint.Style.FILL)
    private val inset = 4f.dpAsPx(resources.displayMetrics).toFloat()
    private val barRect by lazy { RectF(inset, 0f, this.width.toFloat() - inset, this.height.toFloat()) }

    companion object {
        private const val CORNER_RADIUS = 20f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(barRect, CORNER_RADIUS, CORNER_RADIUS, borderPaint)
    }
}