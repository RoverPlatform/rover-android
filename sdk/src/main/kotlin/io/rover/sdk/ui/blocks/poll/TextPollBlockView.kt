package io.rover.sdk.ui.blocks.poll

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.support.v7.widget.AppCompatButton
import android.support.v7.widget.AppCompatTextView
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import io.rover.sdk.data.domain.Color
import io.rover.sdk.data.domain.FontWeight
import io.rover.sdk.data.domain.QuestionStyle
import io.rover.sdk.data.domain.TextAlignment
import io.rover.sdk.data.domain.TextPollBlock
import io.rover.sdk.data.domain.TextPollBlockOptionStyle
import io.rover.sdk.logging.log
import io.rover.sdk.platform.mapToFont
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
import io.rover.sdk.ui.layout.ViewType

internal class TextPollBlockView : LinearLayout, LayoutableView<TextPollBlockViewModel> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // mixins
    private val viewComposition = ViewComposition()
    private val viewBackground = ViewBackground(this)
    private val viewBorder = ViewBorder(this, viewComposition)
    private val viewBlock = ViewBlock(this)

    override var viewModelBinding: MeasuredBindableView.Binding<TextPollBlockViewModel>? by ViewModelBinding { binding, _ ->
        viewBorder.viewModelBinding = binding
        viewBlock.viewModelBinding = binding
        viewBackground.viewModelBinding = binding

        binding?.viewModel?.textPollBlock
    }

    fun createViews(textPollBlock: TextPollBlock) {

    }

    fun createQuestionTextView(text: String): AppCompatTextView {
        return AppCompatTextView(context).apply {
            setText(text)
        }
    }

    fun createOptionsButton(optionText: String): AppCompatButton {
        
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

internal class TextPollBlockViewModel(
    val textPollBlock: TextPollBlock,
    private val blockViewModel: BlockViewModelInterface,
    private val backgroundViewModel: BackgroundViewModelInterface,
    private val borderViewModel: BorderViewModelInterface
) : CompositeBlockViewModelInterface,
    BlockViewModelInterface by blockViewModel,
    BackgroundViewModelInterface by backgroundViewModel,
    BorderViewModelInterface by borderViewModel
{
    override val viewType: ViewType = ViewType.Poll


}

internal class TextPollBlockViewMeasurer(
    private val textPollBlock: TextPollBlock,
    private val measurementService: MeasurementService) : Measurable {

    override fun intrinsicHeight(bounds: RectF): Float {
        val questionHeight = measurementService.measureHeightNeededForMultiLineTextInTextView(
            textPollBlock.question,
            getFontAppearance(textPollBlock.questionStyle.font, textPollBlock.questionStyle.color, textPollBlock.questionStyle.textAlignment),
            bounds.width())
        val optionsHeight = textPollBlock.optionStyle.height * textPollBlock.options.size
        val numberOfVerticalSpaces = 2 + (textPollBlock.options.size - 1)
        val optionSpacing = textPollBlock.optionStyle.verticalSpacing * (numberOfVerticalSpaces)

        return questionHeight + optionsHeight + optionSpacing
    }

    private fun getPaintAlignFromTextAlign(textAlignment: TextAlignment): Paint.Align {
        return when (textAlignment) {
            TextAlignment.Center -> Paint.Align.CENTER
            TextAlignment.Left -> Paint.Align.LEFT
            TextAlignment.Right -> Paint.Align.RIGHT
        }
    }

    private fun getFontAppearance(modelFont: ModelFont, color: Color, alignment: TextAlignment): FontAppearance {
        val font = modelFont.weight.mapToFont()

        return FontAppearance(modelFont.size, font, color.asAndroidColor(), getPaintAlignFromTextAlign(alignment))
    }
}
