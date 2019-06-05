package io.rover.sdk.ui.blocks.poll

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.widget.LinearLayout
import io.rover.sdk.logging.log
import io.rover.sdk.ui.blocks.concerns.ViewComposition
import io.rover.sdk.ui.blocks.concerns.background.ViewBackground
import io.rover.sdk.ui.blocks.concerns.border.ViewBorder
import io.rover.sdk.ui.blocks.concerns.layout.LayoutableView
import io.rover.sdk.ui.blocks.concerns.layout.ViewBlock
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.ViewModelBinding
import io.rover.sdk.data.domain.Font as ModelFont

internal class TextPollBlockView(context: Context?) : LinearLayout(context),
    LayoutableView<TextPollBlockViewModel> {

    // mixins
    private val viewComposition = ViewComposition()
    private val viewBackground = ViewBackground(this)
    private val viewBorder = ViewBorder(this, viewComposition)
    private val viewBlock = ViewBlock(this)
    private val viewTextPoll = ViewTextPoll(this)

    init {
        orientation = VERTICAL
    }

    override var viewModelBinding: MeasuredBindableView.Binding<TextPollBlockViewModel>? by ViewModelBinding { binding, _ ->
        viewBorder.viewModelBinding = binding
        viewBlock.viewModelBinding = binding
        viewBackground.viewModelBinding = binding
        viewTextPoll.viewModelBinding = binding
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        viewComposition.afterOnDraw(canvas)
    }

    @SuppressLint("MissingSuperCall")
    override fun requestLayout() {
        // log.v("Tried to invalidate layout.  Inhibited.")
    }

    override fun forceLayout() {
        log.v("Tried to forcefully invalidate layout.  Inhibited.")
    }
}
