package io.rover.rover.ui.views

import android.content.Context
import android.util.AttributeSet
import io.rover.rover.ui.viewmodels.RowViewModelInterface

class RowView: LayoutableView<RowViewModelInterface> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    private val viewBackground = ViewBackground(this)

    override var viewModel: RowViewModelInterface? = null
        set(viewModel) {
            viewBackground.backgroundViewModel = viewModel
        }
}
