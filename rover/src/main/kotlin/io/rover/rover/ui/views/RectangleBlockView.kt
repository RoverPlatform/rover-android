package io.rover.rover.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import io.rover.rover.ui.BackgroundViewModelInterface
import io.rover.rover.ui.RectangleBlockViewModel

interface ViewBackgroundInterface {
    var backgroundViewModel: BackgroundViewModelInterface?
}

class ViewBackground(
    private val view: View
): ViewBackgroundInterface {
    override var backgroundViewModel: BackgroundViewModelInterface? = null
        set(viewModel) {
            if(viewModel != null) {
                view.setBackgroundColor(viewModel.backgroundColor)
            }
        }
}

class RectangleBlockView: LayoutableView<RectangleBlockViewModel> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    private val viewBackground = ViewBackground(this)

    override var viewModel: RectangleBlockViewModel? = null
        set(viewModel) {
            viewBackground.backgroundViewModel = viewModel
        }
}
