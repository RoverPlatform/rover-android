package io.rover.sdk.experiences.ui.blocks.barcode

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import androidx.appcompat.widget.AppCompatImageView
import android.util.AttributeSet
import io.rover.sdk.experiences.ui.concerns.MeasuredBindableView
import io.rover.sdk.experiences.ui.concerns.ViewModelBinding
import io.rover.sdk.experiences.ui.blocks.concerns.ViewComposition
import io.rover.sdk.experiences.ui.blocks.concerns.layout.LayoutableView
import io.rover.sdk.experiences.ui.blocks.concerns.layout.ViewBlock

internal class BarcodeBlockView : AppCompatImageView, LayoutableView<BarcodeBlockViewModelInterface> {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

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

    override fun draw(canvas: Canvas) {
        viewComposition.beforeDraw(canvas)
        super.draw(canvas)
        viewComposition.afterDraw(canvas)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewComposition.onSizeChanged(w, h, oldw, oldh)
    }
}
