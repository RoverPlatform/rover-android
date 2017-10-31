package io.rover.rover.ui.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import io.rover.rover.ui.viewmodels.RowViewModelInterface

class RowView: View, LayoutableView<RowViewModelInterface> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    private val viewComposition = ViewComposition()
    private val viewBackground = ViewBackground(this)

    override var viewModel: RowViewModelInterface? = null
        set(viewModel) {
            viewBackground.backgroundViewModel = viewModel
        }

    override fun onDraw(canvas: Canvas) {
        viewComposition.beforeOnDraw(canvas)
        super.onDraw(canvas)
        viewComposition.afterOnDraw(canvas)
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        viewComposition.onSizeChanged(w, h, oldw, oldh)
    }
}
