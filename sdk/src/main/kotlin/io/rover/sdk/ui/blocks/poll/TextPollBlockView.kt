package io.rover.sdk.ui.blocks.poll

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.support.v7.widget.AppCompatButton
import android.support.v7.widget.AppCompatTextView
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.rover.sdk.data.domain.Color
import io.rover.sdk.data.domain.FontWeight
import io.rover.sdk.data.domain.QuestionStyle
import io.rover.sdk.data.domain.TextAlignment
import io.rover.sdk.data.domain.TextPollBlock
import io.rover.sdk.data.domain.TextPollBlockOptionStyle
import io.rover.sdk.logging.log
import io.rover.sdk.platform.button
import io.rover.sdk.platform.mapToFont
import io.rover.sdk.platform.setDimens
import io.rover.sdk.platform.textView
import io.rover.sdk.services.MeasurementService
import io.rover.sdk.ui.RectF
import io.rover.sdk.data.domain.Font as ModelFont
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.concerns.ViewComposition
import io.rover.sdk.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.ui.blocks.concerns.background.ViewBackground
import io.rover.sdk.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.sdk.ui.blocks.concerns.border.ViewBorder
import io.rover.sdk.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.sdk.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.sdk.ui.blocks.concerns.layout.LayoutableView
import io.rover.sdk.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.sdk.ui.blocks.concerns.layout.Measurable
import io.rover.sdk.ui.blocks.concerns.layout.ViewBlock
import io.rover.sdk.ui.blocks.concerns.text.Font
import io.rover.sdk.ui.blocks.concerns.text.FontAppearance
import io.rover.sdk.ui.concerns.BindableViewModel
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.ViewModelBinding
import io.rover.sdk.ui.dpAsPx
import io.rover.sdk.ui.layout.ViewType
import io.rover.sdk.ui.pxAsDp

internal class TextPollBlockView : LinearLayout, LayoutableView<TextPollBlockViewModel> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

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
    }

    private fun createViews(textPollBlock: TextPollBlock) {
        val question = textView {
            text = textPollBlock.question
        }

        addView(question)

        val optionStyleHeight = textPollBlock.optionStyle.height.dpAsPx(resources.displayMetrics)
        val optionMarginHeight =
            textPollBlock.optionStyle.verticalSpacing.dpAsPx(resources.displayMetrics)

        textPollBlock.options.forEachIndexed { index, option ->
            val button = button {
                id = index
                background = null
                text = option
                setDimens(
                    width = LayoutParams.MATCH_PARENT,
                    height = optionStyleHeight,
                    topMargin = optionMarginHeight
                )
            }

            addView(button)
        }
    }

    override fun onDraw(canvas: Canvas) {
        viewComposition.beforeOnDraw(canvas)
        super.onDraw(canvas)
        viewComposition.afterOnDraw(canvas)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewComposition.onSizeChanged(w, h, oldw, oldh)
    }

    @SuppressLint("MissingSuperCall")
    override fun requestLayout() {
        // log.v("Tried to invalidate layout.  Inhibited.")
    }

    override fun forceLayout() {
        log.v("Tried to forcefully invalidate layout.  Inhibited.")
    }
}
