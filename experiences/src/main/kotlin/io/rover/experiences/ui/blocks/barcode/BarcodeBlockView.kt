package io.rover.experiences.ui.blocks.barcode

import android.content.Context
import android.graphics.Canvas
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import io.rover.core.ui.concerns.ViewModelBinding
import io.rover.experiences.ui.blocks.concerns.layout.LayoutableView
import io.rover.experiences.ui.blocks.concerns.background.ViewBackground
import io.rover.experiences.ui.blocks.concerns.layout.ViewBlock
import io.rover.experiences.ui.blocks.concerns.border.ViewBorder
import io.rover.experiences.ui.blocks.concerns.ViewComposition
import io.rover.core.ui.concerns.BindableView

class BarcodeBlockView : AppCompatImageView, LayoutableView<BarcodeBlockViewModelInterface> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val viewComposition = ViewComposition()
    private val viewBackground = ViewBackground(this)
    private val viewBorder = ViewBorder(this, viewComposition)
    private val viewBarcode = ViewBarcode(this)
    private val viewBlock = ViewBlock(this, setOf(viewBorder, viewBarcode))

    override var viewModel: BindableView.Binding<BarcodeBlockViewModelInterface>? by ViewModelBinding { binding, _ ->
        viewBorder.viewModel = binding
        viewBarcode.viewModel = binding
        viewBlock.viewModel = binding
        viewBackground.viewModel = binding
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
}
