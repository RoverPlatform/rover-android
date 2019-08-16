package io.rover.sdk.ui.blocks.poll.image

import android.animation.AnimatorSet
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
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.VERTICAL
import android.widget.RelativeLayout
import io.rover.sdk.data.domain.FontWeight
import io.rover.sdk.data.domain.ImagePollBlockOption
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
internal class ImagePollOptionView(context: Context?) : RelativeLayout(context) {
    private val bottomSectionHeight = 40f.dpAsPx(this.resources.displayMetrics)
    private val bottomSectionHorizontalMargin = 8.dpAsPx(resources.displayMetrics)
    private val voteIndicatorViewLeftMargin = 8.dpAsPx(resources.displayMetrics)
    private val votePercentageViewTextSize = 16f
    private val votingIndicatorBarHeight = 8f.dpAsPx(this.resources.displayMetrics)
    private val votingIndicatorBarBottomMargin = 8f.dpAsPx(this.resources.displayMetrics)

    companion object {
        private const val IMAGE_STARTING_ALPHA = 0f
        private const val RESULT_FILL_PROGRESS_DURATION = 1000L
        private const val PROGRESS_ALPHA_DURATION = 167L
        private const val VOTING_BAR_OVERLAY_BAR_FILL_ALPHA_END = 128
        private const val VOTING_BAR_RESULT_FILL_ALPHA_END = 255
        private const val TOP_SECTION_RESULT_OVERLAY_VIEW_ALPHA_END = 0.3f
        private const val VOTING_INDICATOR_ALPHA_END = 1f
        private const val VOTING_PERCENTAGE_ALPHA_END = 1f
    }

    private val shortAnimationDuration = resources.getInteger(android.R.integer.config_shortAnimTime)

    private val optionTextView = textView {
        setLineSpacing(0f, 1.0f)
        includeFontPadding = false
        ellipsize = TextUtils.TruncateAt.END
        maxLines = 1
        gravity = Gravity.CENTER_VERTICAL
        setupLinearLayoutParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = bottomSectionHeight
        )
    }

    private val overallLinearLayout = linearLayout {
        orientation = VERTICAL
        setupRelativeLayoutParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private val votingIndicatorBar = votingIndicatorBar {
        id = ViewCompat.generateViewId()
        visibility = View.GONE
    }

    private val borderView = PollBorderView(context).apply {
        setupRelativeLayoutParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private val votePercentageView = textView {
        visibility = View.GONE
        includeFontPadding = false
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

    private val topSectionResultsOverlayView = view {
        visibility = View.GONE
        setBackgroundColor(Color.BLACK)
        setupRelativeLayoutParams {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    private val voteIndicatorView: AppCompatTextView = textView {
        visibility = View.GONE
        setLineSpacing(0f, 1.0f)
        includeFontPadding = false
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

    init {
        topSection.addView(optionImageView)
        topSection.addView(topSectionResultsOverlayView)
        topSection.addView(votePercentageView)
        topSection.addView(votingIndicatorBar)
        bottomSectionLinear.addView(optionTextView)
        bottomSectionLinear.addView(voteIndicatorView)
        bottomSection.addView(bottomSectionLinear)
        overallLinearLayout.addView(topSection)
        overallLinearLayout.addView(bottomSection)
        addView(overallLinearLayout)
        addView(borderView)
    }

    fun bindOptionView(option: ImagePollBlockOption) {
        bottomSection.layoutParams = (bottomSection.layoutParams as MarginLayoutParams).apply {
            setMargins(bottomSectionHorizontalMargin, 0, bottomSectionHorizontalMargin, 0)
        }

        setBackgroundColor(option.background.color.asAndroidColor())

        bottomSection.gravity = option.text.alignment.convertToGravity()
        bottomSectionLinear.gravity = option.text.alignment.convertToGravity()

        optionTextView.run {
            text = option.text.rawValue
            textSize = option.text.font.size.toFloat()
            setTextColor(option.text.color.asAndroidColor())
            val font = option.text.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }

        votingIndicatorBar.fillPaint = Paint().create(option.resultFillColor.asAndroidColor(), Paint.Style.FILL)
    }

    fun bindOptionImageSize(imageLength: Int) {
        topSection.layoutParams = LinearLayout.LayoutParams(imageLength, imageLength)
        optionImageView.run {
            scaleType = ImageView.ScaleType.FIT_XY
            layoutParams = LayoutParams(imageLength, imageLength)
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

    fun goToResultsState(
        votingShare: Int,
        isSelectedOption: Boolean,
        option: ImagePollBlockOption,
        shouldAnimate: Boolean,
        viewWidth: Int
    ) {
        bindVoteIndicatorBar()
        bindVotePercentageText(votingShare)
        bindVoteIndicatorText(option)

        topSectionResultsOverlayView.visibility = View.VISIBLE

        optionTextView.run {
            gravity = Gravity.CENTER_VERTICAL

            text = option.text.rawValue

            setupLinearLayoutParams(
                width = RelativeLayout.LayoutParams.WRAP_CONTENT,
                height = RelativeLayout.LayoutParams.MATCH_PARENT
            )

            if (isSelectedOption) {
                voteIndicatorView.measure(0, 0)
                maxWidth =
                    viewWidth - voteIndicatorView.measuredWidth - (bottomSectionHorizontalMargin * 2) - (voteIndicatorView.layoutParams as MarginLayoutParams).marginStart
            }
        }

        if (shouldAnimate) {
            performResultsAnimation(votingShare, isSelectedOption)
        } else {
            immediatelyGoToResultsState(votingShare, isSelectedOption, viewWidth)
        }
    }

    private val easeInEaseOutInterpolator = TimeInterpolator { input ->
        val inputSquared = input * input
        inputSquared / (2.0f * (inputSquared - input) + 1.0f)
    }

    private fun immediatelyGoToResultsState(votingShare: Int, isSelectedOption: Boolean, viewWidth: Int) {
        currentVote = votingShare
        if (isSelectedOption) {
            voteIndicatorView.visibility = View.VISIBLE
        }

        votingIndicatorBar.overlayBarFillAlpha = VOTING_BAR_OVERLAY_BAR_FILL_ALPHA_END
        votingIndicatorBar.resultFillAlpha = VOTING_BAR_RESULT_FILL_ALPHA_END
        topSectionResultsOverlayView.alpha = TOP_SECTION_RESULT_OVERLAY_VIEW_ALPHA_END
        voteIndicatorView.alpha = VOTING_INDICATOR_ALPHA_END
        votePercentageView.alpha = VOTING_PERCENTAGE_ALPHA_END
        votePercentageView.text = "$votingShare%"
        votingIndicatorBar.viewWidth = viewWidth.toFloat()
        votingIndicatorBar.barValue = votingShare.toFloat()
    }

    // used to set starting point for update animations
    private var currentVote = 0

    fun updateResults(votingShare: Int) {
        val resultProgressFillAnimator = ValueAnimator.ofFloat(currentVote.toFloat(), votingShare.toFloat()).apply {
            duration = RESULT_FILL_PROGRESS_DURATION
            addUpdateListener {
                val animatedValue = it.animatedValue as Float
                votePercentageView.text = "${animatedValue.toInt()}%"
                votingIndicatorBar.barValue = animatedValue
            }

            interpolator = easeInEaseOutInterpolator
        }

        if (resultsAnimation.isRunning) resultProgressFillAnimator.startDelay = 1000L
        resultProgressFillAnimator.start()
        currentVote = votingShare
    }

    private var resultsAnimation = AnimatorSet()

    private fun performResultsAnimation(votingShare: Int, isSelectedOption: Boolean) {
        currentVote = votingShare

        if (isSelectedOption) {
            voteIndicatorView.visibility = View.VISIBLE
        }

        val whiteVotingBarAlphaAnimator = ValueAnimator.ofInt(0, 128).apply {
            duration = PROGRESS_ALPHA_DURATION
            addUpdateListener {
                val animatedValue = it.animatedValue as Int
                votingIndicatorBar.overlayBarFillAlpha = animatedValue
            }
        }

        val resultVotingBarAlphaAnimator = ValueAnimator.ofInt(0, 255).apply {
            duration = PROGRESS_ALPHA_DURATION
            addUpdateListener {
                votingIndicatorBar.resultFillAlpha = it.animatedValue as Int
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
        resultsAnimation = AnimatorSet().apply {
            playTogether(alphaAnimator, whiteVotingBarAlphaAnimator, overlayAlphaAnimator, resultProgressFillAnimator,
                resultVotingBarAlphaAnimator)
            interpolator = easeInEaseOutInterpolator
        }

        resultsAnimation.start()
    }

    private fun bindVotePercentageText(votingShare: Int) {
        votePercentageView.run {
            textSize = votePercentageViewTextSize
            setTextColor(Color.WHITE)
            setLineSpacing(0f, 1.0f)
            includeFontPadding = false
            text = "$votingShare%"
            val font = FontWeight.Medium.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
            visibility = View.VISIBLE
            setupRelativeLayoutParams(
                width = RelativeLayout.LayoutParams.WRAP_CONTENT, height = 24.dpAsPx(resources.displayMetrics)
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

    private fun bindVoteIndicatorText(option: ImagePollBlockOption) {
        voteIndicatorView.run {
            text = "\u2022"
            textSize = option.text.font.size.toFloat()
            setTextColor(option.text.color.asAndroidColor())
            val font = option.text.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }

    fun initializeOptionViewLayout(option: ImagePollBlockOption, viewHeight: Int) {
        gravity = Gravity.CENTER_VERTICAL
        alpha = option.opacity.toFloat()
        setBackgroundColor(Color.TRANSPARENT)

        val adjustedBorderRadius = if (option.border.radius.toFloat() > viewHeight.toFloat() / 2) {
            viewHeight / 2
        } else {
            option.border.radius
        }

        val adjustedOption = option.copy(border = option.border.copy(radius = adjustedBorderRadius))

        val borderRadius = adjustedOption.border.radius.dpAsPx(resources.displayMetrics).toFloat()
        val borderStrokeWidth = adjustedOption.border.width.dpAsPx(resources.displayMetrics).toFloat()

        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        roundRect = RoundRect(rect, borderRadius, borderStrokeWidth)
        borderView.border = adjustedOption.border
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

    companion object {
        private const val BAR_HEIGHT = 8f
        private const val CORNER_RADIUS = 4f
    }

    private val calculatedCornerRadius = CORNER_RADIUS.dpAsPx(this.resources.displayMetrics).toFloat()

    var overlayBarFillAlpha = 0
        set(value) {
            field = value
            overlayBarPaint.alpha = overlayBarFillAlpha
        }
    var resultFillAlpha = 0
        set(value) {
            field = value
            fillPaint.alpha = field
        }
    var viewWidth: Float? = null
    var barValue = 0f
        set(value) {
            field = value
            val barWidth = ((viewWidth ?: this.width.toFloat()) - (inset * 2)) * (barValue / 100)
            barRect = RectF(0f, 0f, barWidth + inset, BAR_HEIGHT.dpAsPx(this.resources.displayMetrics).toFloat())
            invalidate()
        }
    var fillPaint = Paint()

    private var overlayBarPaint = Paint().create(Color.WHITE, Paint.Style.FILL)
    private val inset = 4f.dpAsPx(resources.displayMetrics).toFloat()
    private var barRect = RectF(0f, 0f, 0f, BAR_HEIGHT.dpAsPx(this.resources.displayMetrics).toFloat())
    private val overlayRect by lazy { RectF(inset, 0f, (viewWidth ?: this.width.toFloat()) - inset, BAR_HEIGHT.dpAsPx(this.resources.displayMetrics).toFloat()) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(overlayRect, calculatedCornerRadius, calculatedCornerRadius, overlayBarPaint)
        canvas.drawRoundRect(barRect, calculatedCornerRadius, calculatedCornerRadius, fillPaint)
    }

    override fun draw(canvas: Canvas?) {
        canvas?.clipPath(Path().apply {
            addRoundRect(overlayRect, calculatedCornerRadius, calculatedCornerRadius, Path.Direction.CW)
        })
        super.draw(canvas)
    }
}
