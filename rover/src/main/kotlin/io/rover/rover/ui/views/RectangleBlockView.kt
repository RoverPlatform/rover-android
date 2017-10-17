package io.rover.rover.ui.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import io.rover.rover.ui.BlockViewModelInterface
import io.rover.rover.ui.RectangleBlockViewModel


class RectangleBlockView: LayoutableView<RectangleBlockViewModel> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override var viewModel: RectangleBlockViewModel? = null
        set(value) {
            if(value != null) {
                setBackgroundColor(value.backgroundColor.asAndroidColor())
            }
            field = value
        }
}
