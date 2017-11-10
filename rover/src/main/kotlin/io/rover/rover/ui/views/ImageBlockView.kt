package io.rover.rover.ui.views

import android.content.Context
import android.graphics.Canvas
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import io.rover.rover.core.logging.log
import io.rover.rover.ui.viewmodels.ImageBlockViewModelInterface

class ImageBlockView: AppCompatImageView, LayoutableView<ImageBlockViewModelInterface> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // mixins (TODO: injections)
    private val viewComposition = ViewComposition()
    private val viewBlock = ViewBlock(this)
    private val viewBackground = ViewBackground(this)
    private val viewBorder = ViewBorder(this, viewComposition, viewBlock)
    private val viewImage = ViewImage(this)

    override var viewModel: ImageBlockViewModelInterface? = null
        set(viewModel) {
            viewBlock.blockViewModel = viewModel
            viewBackground.backgroundViewModel = viewModel
            viewBorder.borderViewModel = viewModel
            viewImage.imageViewModel = viewModel
        }

    override fun onDraw(canvas: Canvas) {
        viewComposition.beforeOnDraw(canvas)
        super.onDraw(canvas)
        viewComposition.afterOnDraw(canvas)
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        viewComposition.onSizeChanged(w, h, oldw, oldh)
    }
}
