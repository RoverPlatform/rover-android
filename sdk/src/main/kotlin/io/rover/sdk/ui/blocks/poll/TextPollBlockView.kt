package io.rover.sdk.ui.blocks.poll

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.support.v7.widget.AppCompatTextView
import android.view.Gravity
import android.view.Gravity.CENTER_VERTICAL
import android.view.ViewGroup
import android.widget.LinearLayout
import io.rover.sdk.data.domain.QuestionStyle
import io.rover.sdk.data.domain.TextPollBlock
import io.rover.sdk.data.domain.TextPollBlockOptionStyle
import io.rover.sdk.logging.log
import io.rover.sdk.platform.button
import io.rover.sdk.platform.mapToFont
import io.rover.sdk.platform.optionView
import io.rover.sdk.platform.setDimens
import io.rover.sdk.platform.textView
import io.rover.sdk.streams.androidLifecycleDispose
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.concerns.ViewComposition
import io.rover.sdk.ui.blocks.concerns.background.ViewBackground
import io.rover.sdk.ui.blocks.concerns.border.ViewBorder
import io.rover.sdk.ui.blocks.concerns.layout.LayoutableView
import io.rover.sdk.ui.blocks.concerns.layout.ViewBlock
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.ViewModelBinding
import io.rover.sdk.ui.dpAsPx
import io.rover.sdk.ui.pxAsDp
import io.rover.sdk.data.domain.Font as ModelFont



internal class TextPollBlockView(context: Context?) : LinearLayout(context), LayoutableView<TextPollBlockViewModel> {

    // mixins
    private val viewComposition = ViewComposition()
    private val viewBackground = ViewBackground(this)
    private val viewBorder = ViewBorder(this, viewComposition)
    private val viewBlock = ViewBlock(this)

    init {
        orientation = VERTICAL
    }

    override var viewModelBinding: MeasuredBindableView.Binding<TextPollBlockViewModel>? by ViewModelBinding { binding, _ ->
        viewBorder.viewModelBinding = binding
        viewBlock.viewModelBinding = binding
        viewBackground.viewModelBinding = binding

        binding?.viewModel?.textPollBlock?.let {
            createViews(it)
        }

        binding?.viewModel?.pollState?.androidLifecycleDispose(this)?.subscribe {
//            when (it) {
//                is PollState.LoadingState -> {}
//                is PollState.VotingState -> {
//                    createViews(it.textPollBlock)
//                }
//                is PollState.ResultState -> {}
//            }
        }
    }

    private fun createViews(textPollBlock: TextPollBlock) {
        val question = textView {
            text = textPollBlock.question
            setTextStyleProperties(textPollBlock.questionStyle)
        }

        addView(question)

        val optionStyle = textPollBlock.optionStyle
        val optionStyleHeight = optionStyle.height.dpAsPx(resources.displayMetrics)
        val optionMarginHeight = optionStyle.verticalSpacing.dpAsPx(resources.displayMetrics)
        val borderWidth = optionStyle.borderWidth.dpAsPx(resources.displayMetrics)

        textPollBlock.options.forEachIndexed { index, option ->
            val button = optionView {
                id = index
                alpha = optionStyle.opacity.toFloat()
                setBackgroundColor(optionStyle.backgroundColor.asAndroidColor())
                setDimens(
                    width = LayoutParams.MATCH_PARENT,
                    height = optionStyleHeight + borderWidth,
                    topMargin = optionMarginHeight,
                    padding = borderWidth
                )
                createViews(option, textPollBlock.optionStyle)
                setResult(optionStyle.resultFillColor.asAndroidColor())
            }

            addView(button)
        }
    }

    private fun AppCompatTextView.setTextStyleProperties(questionStyle: QuestionStyle) {
        gravity = questionStyle.textAlignment.convertToGravity()
        textSize = questionStyle.font.size.toFloat()
        setTextColor(questionStyle.color.asAndroidColor())
        val font = questionStyle.font.weight.mapToFont()
        typeface = Typeface.create(font.fontFamily, font.fontStyle)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        viewComposition.afterOnDraw(canvas)
    }

    @SuppressLint("MissingSuperCall")
    override fun requestLayout() {
        // log.v("Tried to invalidate layout.  Inhibited.")
    }

    override fun forceLayout() {
        log.v("Tried to forcefully invalidate layout.  Inhibited.")
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

        paint = Paint().apply {
            color = optionStyle.borderColor.asAndroidColor()
            strokeWidth = optionStyle.borderWidth.dpAsPx(resources.displayMetrics).toFloat()
            style = Paint.Style.STROKE
        }

        addView(question)
    }

    private var paint = Paint()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    fun setResult(resultColor: Int) {

    }
}




