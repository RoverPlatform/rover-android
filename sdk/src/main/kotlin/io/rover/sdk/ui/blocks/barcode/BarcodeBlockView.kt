package io.rover.sdk.ui.blocks.barcode

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.ViewModelBinding
import io.rover.sdk.ui.blocks.concerns.ViewComposition
import io.rover.sdk.ui.blocks.concerns.layout.LayoutableView
import io.rover.sdk.ui.blocks.concerns.layout.ViewBlock

internal class BarcodeBlockView : AppCompatImageView, LayoutableView<BarcodeBlockViewModelInterface> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val viewComposition = ViewComposition()
    private val viewBarcode = ViewBarcode(this)
    private val viewBlock = ViewBlock(this)

    init {
        // Barcodes must always be on a solid white background.
        this.setBackgroundColor(Color.WHITE)
    }

    override var viewModelBinding: MeasuredBindableView.Binding<BarcodeBlockViewModelInterface>? by ViewModelBinding { binding, _ ->
        viewBarcode.viewModelBinding = binding
        viewBlock.viewModelBinding = binding
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
