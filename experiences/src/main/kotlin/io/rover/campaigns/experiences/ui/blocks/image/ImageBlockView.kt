package io.rover.campaigns.experiences.ui.blocks.image

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import androidx.appcompat.widget.AppCompatImageView
import android.util.AttributeSet
import io.rover.campaigns.experiences.logging.log
import io.rover.campaigns.experiences.ui.blocks.concerns.layout.LayoutableView
import io.rover.campaigns.experiences.ui.blocks.concerns.background.ViewBackground
import io.rover.campaigns.experiences.ui.blocks.concerns.layout.ViewBlock
import io.rover.campaigns.experiences.ui.blocks.concerns.border.ViewBorder
import io.rover.campaigns.experiences.ui.blocks.concerns.ViewComposition
import io.rover.campaigns.experiences.ui.concerns.MeasuredBindableView
import io.rover.campaigns.experiences.ui.concerns.ViewModelBinding

internal class ImageBlockView : AppCompatImageView, LayoutableView<ImageBlockViewModelInterface> {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // mixins (TODO: injections)
    private val viewComposition = ViewComposition()

    private val viewBackground = ViewBackground(this)
    private val viewBorder = ViewBorder(this, viewComposition)
    private val viewBlock = ViewBlock(this)
    private val viewImage = ViewImage(this)

    override var viewModelBinding: MeasuredBindableView.Binding<ImageBlockViewModelInterface>? by ViewModelBinding { binding, _ ->
        viewBorder.viewModelBinding = binding
        viewBlock.viewModelBinding = binding
        viewBackground.viewModelBinding = binding
        viewImage.viewModelBinding = binding
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

    @SuppressLint("MissingSuperCall")
    override fun requestLayout() {
        // log.v("Tried to invalidate layout.  Inhibited.")
    }

    override fun forceLayout() {
        log.v("Tried to forcefully invalidate layout.  Inhibited.")
    }
}
