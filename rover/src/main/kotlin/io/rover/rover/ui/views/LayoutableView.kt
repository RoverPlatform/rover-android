package io.rover.rover.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import io.rover.rover.ui.LayoutableViewModel


abstract class LayoutableView<VM: LayoutableViewModel> : View {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    abstract var viewModel: VM?
}
