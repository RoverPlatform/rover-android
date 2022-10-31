package io.rover.experiences.ui.blocks.poll.image

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.LinearLayout
import io.rover.experiences.streams.PublishSubject
import io.rover.experiences.ui.blocks.concerns.ViewComposition
import io.rover.experiences.ui.blocks.concerns.background.ViewBackground
import io.rover.experiences.ui.blocks.concerns.border.ViewBorder
import io.rover.experiences.ui.blocks.concerns.layout.LayoutableView
import io.rover.experiences.ui.blocks.concerns.layout.ViewBlock
import io.rover.experiences.ui.concerns.MeasuredBindableView
import io.rover.experiences.ui.concerns.ViewModelBinding

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
        viewComposition.beforeDraw(canvas)
        super.draw(canvas)
        viewComposition.afterDraw(canvas)
    }
}
