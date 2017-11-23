package io.rover.rover.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import io.rover.rover.core.logging.log
import io.rover.rover.ui.viewmodels.ImageBlockViewModelInterface

class ImageBlockView : AppCompatImageView, LayoutableView<ImageBlockViewModelInterface> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // mixins (TODO: injections)
    private val viewComposition = ViewComposition()

    private val viewBackground = ViewBackground(this)
    private val viewBorder = ViewBorder(this, viewComposition)
    private val viewBlock = ViewBlock(this, setOf(viewBorder))
    private val viewImage = ViewImage(this)

    override var viewModel: ImageBlockViewModelInterface? = null
        set(viewModel) {
            viewBorder.borderViewModel = viewModel
            viewBlock.blockViewModel = viewModel
            viewBackground.backgroundViewModel = viewModel
            viewImage.imageBlockViewModel = viewModel
        }

    override fun onDraw(canvas: Canvas) {
        viewComposition.beforeOnDraw(canvas)
        super.onDraw(canvas)
        viewComposition.afterOnDraw(canvas)
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        viewComposition.onSizeChanged(w, h, oldw, oldh)
    }

    @SuppressLint("MissingSuperCall")
    override fun requestLayout() {
        log.v("Tried to invalidate layout.  Inhibited.")
    }

    override fun forceLayout() {
        log.v("Tried to forcefully invalidate layout.  Inhibited.")
    }
}
