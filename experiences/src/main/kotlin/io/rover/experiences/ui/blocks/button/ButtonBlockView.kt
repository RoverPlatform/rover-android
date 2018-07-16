package io.rover.experiences.ui.blocks.button

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.support.v7.widget.AppCompatTextView
import android.util.AttributeSet
import io.rover.experiences.ui.blocks.concerns.ViewComposition
import io.rover.experiences.ui.blocks.concerns.background.ViewBackground
import io.rover.experiences.ui.blocks.concerns.border.ViewBorder
import io.rover.experiences.ui.blocks.concerns.layout.LayoutableView
import io.rover.experiences.ui.blocks.concerns.layout.ViewBlock
import io.rover.experiences.ui.blocks.concerns.text.AndroidRichTextToSpannedTransformer
import io.rover.experiences.ui.blocks.concerns.text.ViewText
import io.rover.core.logging.log
import io.rover.core.ui.concerns.BindableView
import io.rover.core.ui.concerns.ViewModelBinding

// API compatibility is managed at runtime in a way that Android lint's static analysis is not able
// to pick up.
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@SuppressLint("NewApi")
class ButtonBlockView : AppCompatTextView, LayoutableView<ButtonBlockViewModelInterface> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override var viewModel: BindableView.Binding<ButtonBlockViewModelInterface>? by ViewModelBinding { binding, _ ->
        viewBackground.viewModel = binding
        viewBorder.viewModel = binding
        viewBlock.viewModel = binding
        viewBackground.viewModel = binding
        viewText.viewModel = binding
    }

    private val viewComposition = ViewComposition()
    private val viewBackground = ViewBackground(this)
    private val viewBorder = ViewBorder(this, viewComposition)
    private val viewBlock = ViewBlock(this, setOf(viewBorder))
    private val viewText = ViewText(this, AndroidRichTextToSpannedTransformer())

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
