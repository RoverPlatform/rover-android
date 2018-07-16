package io.rover.experiences.ui.blocks.image

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import io.rover.core.logging.log
import io.rover.experiences.ui.blocks.concerns.layout.LayoutableView
import io.rover.experiences.ui.blocks.concerns.background.ViewBackground
import io.rover.experiences.ui.blocks.concerns.layout.ViewBlock
import io.rover.experiences.ui.blocks.concerns.border.ViewBorder
import io.rover.experiences.ui.blocks.concerns.ViewComposition
import io.rover.core.ui.concerns.BindableView
import io.rover.core.ui.concerns.ViewModelBinding

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

    override var viewModel: BindableView.Binding<ImageBlockViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->
        viewBorder.viewModel = binding
        viewBlock.viewModel = binding
        viewBackground.viewModel = binding
        viewImage.viewModel = binding
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
