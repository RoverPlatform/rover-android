package io.rover.rover.ui.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import io.rover.rover.ui.viewmodels.TextBlockViewModelInterface

class TextBlockView: TextView, LayoutableView<TextBlockViewModelInterface> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    // mixins
    private val viewComposition = ViewComposition()
    private val viewBlock = ViewBlock(this)
    private val viewBackground = ViewBackground(this)
    private val viewBorder = ViewBorder(this, viewComposition)
    private val viewText = ViewText(this)

    override var viewModel: TextBlockViewModelInterface? = null
        set(viewModel) {
            viewBlock.blockViewModel = viewModel
            viewBackground.backgroundViewModel = viewModel
            viewBorder.borderViewModel = viewModel
            viewText.textViewModel = viewModel
        }

    override val view: View
        get() = this

    override fun onDraw(canvas: Canvas) {
        viewComposition.beforeOnDraw(canvas)
        super.onDraw(canvas)
        viewComposition.afterOnDraw(canvas)
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        viewComposition.onSizeChanged(w, h, oldw, oldh)
    }
}