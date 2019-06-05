package io.rover.sdk.ui.blocks.poll

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.support.v7.widget.AppCompatTextView
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import io.rover.sdk.data.domain.QuestionStyle
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
    override var viewModelBinding: MeasuredBindableView.Binding<TextPollViewModelInterface>? by ViewModelBinding { binding, _ ->
        binding?.viewModel?.let {


            val optionStyleHeight = it.textPollBlock.optionStyle.height
            val borderWidth = it.textPollBlock.optionStyle.borderWidth

            val optionViews = createOptionViews(it.textPollBlock)

            startListeningForOptionImageUpdates(it.optionBackgroundViewModel, optionViews)

            view.addView(createQuestion(it.textPollBlock))
            optionViews.forEach { optionView -> view.addView(optionView) }

            val optionHeight = MeasuredSize(
                width = view.height.pxAsDp(view.resources.displayMetrics),
                height = optionStyleHeight + (borderWidth * 2).toFloat(),
                density = view.resources.displayMetrics.density
            )

            it.optionBackgroundViewModel.informDimensions(optionHeight)
        }
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
                id = index
                alpha = optionStyle.opacity.toFloat()
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setDimens(
                    width = LinearLayout.LayoutParams.MATCH_PARENT,
                    height = optionStyleHeight + (borderWidth * 2),
                    topMargin = optionMarginHeight,
                    leftPadding = borderWidth
                )
                createViews(option, textPollBlock.optionStyle)
                setResult(optionStyle.resultFillColor.asAndroidColor())
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

internal class OptionView(context: Context?) : LinearLayout(context) {

    companion object {
        private const val TEXT_ID = 0
        private const val RESULT_PERCENT_ID = 1
    }

    fun createViews(option: String, optionStyle: TextPollBlockOptionStyle) {

        val question = textView {
            id = TEXT_ID
            text = option
            textSize = optionStyle.font.size.toFloat()
            setTextColor(optionStyle.color.asAndroidColor())
            val font = optionStyle.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }

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

        addView(question)
    }

    private var strokeWidthX = 0f
    private var borderRadius = 0f
    private var paint = Paint()
    private var fillColorPaint = Paint()
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

//        val path1 = Path().addRoundRect(rectWithBorders, borderRadius, borderRadius, Path.Direction.CW)
//        val path2 = Path().addRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), Path.Direction.CW)


        if (backgroundImage == null) {
            canvas.drawRoundRect(rectWithBorders, borderRadius, borderRadius, fillColorPaint)
        }

        if (strokeWidthX != 0f) {
            canvas.drawRoundRect(rectWithBorders, borderRadius, borderRadius, paint)
        }
    }

    fun setResult(resultColor: Int) {

    }
}

internal interface ViewTextPollInterface : MeasuredBindableView<TextPollViewModelInterface>