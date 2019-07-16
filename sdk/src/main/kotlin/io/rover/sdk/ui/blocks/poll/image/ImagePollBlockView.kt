package io.rover.sdk.ui.blocks.poll.image

import android.content.Context
import android.graphics.Canvas
import android.widget.LinearLayout
import io.rover.sdk.ui.blocks.concerns.ViewComposition
import io.rover.sdk.ui.blocks.concerns.background.ViewBackground
import io.rover.sdk.ui.blocks.concerns.border.ViewBorder
import io.rover.sdk.ui.blocks.concerns.layout.LayoutableView
import io.rover.sdk.ui.blocks.concerns.layout.ViewBlock
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.ViewModelBinding

internal class ImagePollBlockView(context: Context?) : LinearLayout(context),
    LayoutableView<ImagePollBlockViewModel> {

    // mixins
    private val viewComposition = ViewComposition()
    private val viewBackground = ViewBackground(this)
    private val viewBorder = ViewBorder(this, viewComposition)
    private val viewBlock = ViewBlock(this)
    private val viewImagePoll = ViewImagePoll(this)

    init {
        orientation = VERTICAL
    }

    override var viewModelBinding: MeasuredBindableView.Binding<ImagePollBlockViewModel>? by ViewModelBinding { binding, _ ->
        viewBorder.viewModelBinding = binding
        viewBlock.viewModelBinding = binding
        viewBackground.viewModelBinding = binding
        viewImagePoll.viewModelBinding = binding
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        viewComposition.afterOnDraw(canvas)
    }
}
