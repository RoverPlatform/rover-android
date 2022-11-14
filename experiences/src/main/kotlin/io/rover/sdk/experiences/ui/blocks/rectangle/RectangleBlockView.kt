package io.rover.sdk.experiences.ui.blocks.rectangle

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import androidx.annotation.RequiresApi
import android.util.AttributeSet
import android.view.View
import io.rover.sdk.experiences.logging.log
import io.rover.sdk.experiences.ui.concerns.MeasuredBindableView
import io.rover.sdk.experiences.ui.concerns.ViewModelBinding
import io.rover.sdk.experiences.ui.blocks.concerns.ViewComposition
import io.rover.sdk.experiences.ui.blocks.concerns.background.ViewBackground
import io.rover.sdk.experiences.ui.blocks.concerns.border.ViewBorder
import io.rover.sdk.experiences.ui.blocks.concerns.layout.LayoutableView
import io.rover.sdk.experiences.ui.blocks.concerns.layout.ViewBlock

internal class RectangleBlockView : View, LayoutableView<RectangleBlockViewModelInterface> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    // mixins
    private val viewComposition = ViewComposition()
    private val viewBackground = ViewBackground(this)
    private val viewBorder = ViewBorder(this, viewComposition)
    private val viewBlock = ViewBlock(this)

    override var viewModelBinding: MeasuredBindableView.Binding<RectangleBlockViewModelInterface>? by ViewModelBinding { binding, _ ->
        viewBackground.viewModelBinding = binding
        viewBorder.viewModelBinding = binding
        viewBlock.viewModelBinding = binding
    }

    override val view: View
        get() = this

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
