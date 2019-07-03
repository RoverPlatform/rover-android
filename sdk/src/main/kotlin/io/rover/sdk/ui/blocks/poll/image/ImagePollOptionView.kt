package io.rover.sdk.ui.blocks.poll.image

import android.animation.AnimatorSet
import android.animation.LayoutTransition
import android.animation.LayoutTransition.CHANGE_APPEARING
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
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
import io.rover.sdk.platform.view
import io.rover.sdk.platform.votingIndicatorBar
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.poll.PollBorderView
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

    companion object {
        private const val IMAGE_STARTING_ALPHA = 0f
        private const val RESULT_FILL_PROGRESS_DURATION = 1000L
        private const val PROGRESS_ALPHA_DURATION = 167L
    }

    private val shortAnimationDuration = resources.getInteger(android.R.integer.config_shortAnimTime)

    private val optionTextView = textView {
        id = ViewCompat.generateViewId()
        ellipsize = TextUtils.TruncateAt.END
        maxLines = 1
        gravity = Gravity.CENTER_VERTICAL
        setupLinearLayoutParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = bottomSectionHeight
        )
    }

    private val votingIndicatorBar = votingIndicatorBar {
        id = ViewCompat.generateViewId()
        visibility = View.GONE
    }

    private val borderView = PollBorderView(context).apply {
        setupLinearLayoutParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = ViewGroup.LayoutParams.MATCH_PARENT
        )
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
        // TODO: Change this to reflect the option style text alignment
        gravity = Gravity.CENTER
        setupLinearLayoutParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = bottomSectionHeight
        )
    }

    private val bottomSectionLinear = linearLayout {
        // TODO: Change this to reflect the option style text alignment
        gravity = Gravity.CENTER
        setupRelativeLayoutParams(
            width = ViewGroup.LayoutParams.WRAP_CONTENT,
            height = bottomSectionHeight
        )
    }

    private val topSectionResultsOverlayView = view {
        id = ViewCompat.generateViewId()
        visibility = View.GONE
        setBackgroundColor(Color.BLACK)
        setupRelativeLayoutParams {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
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
    private var halfBorderWidth = 0f

    init {
        orientation = VERTICAL
        topSection.addView(optionImageView)
        topSection.addView(topSectionResultsOverlayView)
        topSection.addView(votePercentageView)
        topSection.addView(votingIndicatorBar)
        bottomSectionLinear.addView(optionTextView)
        bottomSectionLinear.addView(voteIndicatorView)
        bottomSection.addView(bottomSectionLinear)
        addView(topSection)
        addView(bottomSection)
    }

    fun bindOptionView(option: String, optionStyle: ImagePollBlockOptionStyle) {
        bottomSection.layoutParams = (bottomSection.layoutParams as MarginLayoutParams).apply {
            setMargins(bottomSectionHorizontalMargin, 0, bottomSectionHorizontalMargin, 0)
        }

        optionTextView.run {
            text = option
            textSize = optionStyle.font.size.toFloat()
            setTextColor(optionStyle.color.asAndroidColor())
            val font = optionStyle.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }

        votingIndicatorBar.fillPaint = Paint().create(optionStyle.resultFillColor.asAndroidColor(), Paint.Style.FILL)
    }

    fun bindOptionImageSize(imageLength: Int) {
        topSection.layoutParams = LayoutParams(imageLength, imageLength)
        optionImageView.run {
            scaleType = ImageView.ScaleType.FIT_XY
            layoutParams = RelativeLayout.LayoutParams(imageLength, imageLength)
        }
    }

    fun bindOptionImage(bitmap: Bitmap, shouldFade: Boolean, opacity: Float) {
        optionImageView.run {
            alpha = IMAGE_STARTING_ALPHA
            setImageBitmap(bitmap)
            if (shouldFade) {
                animate()
                    .alpha(opacity)
                    .setDuration(shortAnimationDuration.toLong())
                    .start()
            } else {
                alpha = opacity
            }
        }
    }

    fun goToResultsState(votingShare: Int, isSelectedOption: Boolean, optionStyle: ImagePollBlockOptionStyle) {
        bindVoteIndicatorBar()
        bindVotePercentageText(votingShare)
        bindVoteIndicatorText(optionStyle)

        topSectionResultsOverlayView.visibility = View.VISIBLE

        optionTextView.run {
            gravity = Gravity.CENTER_VERTICAL
            setupLinearLayoutParams(
                width = RelativeLayout.LayoutParams.WRAP_CONTENT,
                height = RelativeLayout.LayoutParams.MATCH_PARENT
            )

            if (isSelectedOption) {
                voteIndicatorView.measure(0, 0)
                maxWidth =
                    bottomSection.width - voteIndicatorView.measuredWidth - (voteIndicatorView.layoutParams as MarginLayoutParams).marginStart
            }
        }

        performResultsAnimation(votingShare, isSelectedOption)
    }

    private fun performResultsAnimation(votingShare: Int, isSelectedOption: Boolean) {
        val easeInEaseOutInterpolator = TimeInterpolator { input ->
            val inputSquared = input * input
            inputSquared / (2.0f * (inputSquared - input) + 1.0f)
        }

        bottomSectionLinear.layoutTransition = LayoutTransition().apply {
            setDuration(PROGRESS_ALPHA_DURATION)
            setInterpolator(CHANGE_APPEARING, easeInEaseOutInterpolator)
        }

        if (isSelectedOption) {
            voteIndicatorView.visibility = View.VISIBLE
        }

        val whiteVotingBarAlphaAnimator = ValueAnimator.ofInt(0, 128).apply {
            duration = PROGRESS_ALPHA_DURATION
            addUpdateListener {
                val animatedValue = it.animatedValue as Int
                votingIndicatorBar.fillAlpha = animatedValue
            }
        }

        val overlayAlphaAnimator = ValueAnimator.ofFloat(0f, 0.3f).apply {
            duration = PROGRESS_ALPHA_DURATION
            addUpdateListener {
                topSectionResultsOverlayView.alpha = it.animatedValue as Float
            }
        }

        val alphaAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PROGRESS_ALPHA_DURATION
            addUpdateListener {
                voteIndicatorView.alpha = it.animatedFraction
                votePercentageView.alpha = it.animatedFraction
            }
        }

        val resultProgressFillAnimator = ValueAnimator.ofFloat(0f, votingShare.toFloat()).apply {
            duration = RESULT_FILL_PROGRESS_DURATION
            addUpdateListener {
                val animatedValue = it.animatedValue as Float
                votePercentageView.text = "${animatedValue.toInt()}%"
                votingIndicatorBar.barValue = animatedValue
            }
        }
        AnimatorSet().apply {
            playTogether(alphaAnimator, whiteVotingBarAlphaAnimator, overlayAlphaAnimator, resultProgressFillAnimator)
            interpolator = easeInEaseOutInterpolator
        }.start()
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

    private fun bindVoteIndicatorBar() {
        votingIndicatorBar.run {
            visibility = View.VISIBLE
            setupRelativeLayoutParams(
                width = RelativeLayout.LayoutParams.MATCH_PARENT,
                height = votingIndicatorBarHeight,
                bottomMargin = votingIndicatorBarBottomMargin
            ) {
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

        borderView.borderPaint = Paint().create(
            optionStyle.border.color.asAndroidColor(),
            Paint.Style.STROKE,
            borderStrokeWidth
        )

        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        roundRect = RoundRect(rect, borderRadius, borderStrokeWidth)
        halfBorderWidth = (optionStyle.border.width / 2).dpAsPx(resources.displayMetrics).toFloat()
        borderView.borderRoundRect = roundRect?.copy(
            rectF = RectF(
                halfBorderWidth, halfBorderWidth,
                width.toFloat() - halfBorderWidth,
                height.toFloat() - halfBorderWidth
            )
        )
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
        borderView.borderRoundRect = roundRect?.copy(
            rectF = RectF(
                halfBorderWidth, halfBorderWidth,
                width.toFloat() - halfBorderWidth,
                height.toFloat() - halfBorderWidth
            )
        )
    }

    override fun dispatchDraw(canvas: Canvas?) {
        super.dispatchDraw(canvas)
        borderView.draw(canvas)
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

class VotingIndicatorBar(context: Context?) : View(context) {
    var fillAlpha = 0
        set(value) {
            field = value
            overlayBarPaint = Paint().apply {
                color = Color.WHITE
                Paint.Style.FILL
                alpha = fillAlpha
            }
        }
    var barValue = 0f
        set(value) {
            field = value
            barRect = RectF(inset, 0f, ((width.toFloat() - inset) * (barValue / 100)), height.toFloat())
            invalidate()
        }
    var fillPaint = Paint()

    private var overlayBarPaint = Paint()
    private val inset = 4f.dpAsPx(resources.displayMetrics).toFloat()
    private var barRect = RectF(inset, 0f, ((width.toFloat() - inset) * barValue), height.toFloat())
    private val overlayRect by lazy { RectF(inset, 0f, width.toFloat() - inset, height.toFloat()) }

    companion object {
        private const val CORNER_RADIUS = 20f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(overlayRect, CORNER_RADIUS, CORNER_RADIUS, overlayBarPaint)
        canvas.drawRoundRect(barRect, CORNER_RADIUS, CORNER_RADIUS, fillPaint)
    }
}