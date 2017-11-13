package io.rover.rover.ui.views

import android.view.View
import io.rover.rover.ui.viewmodels.BackgroundViewModelInterface

class ViewBackground(
    private val view: View
) : ViewBackgroundInterface {
    override var backgroundViewModel: BackgroundViewModelInterface? = null
        set(viewModel) {
            if (viewModel != null) {
                view.setBackgroundColor(viewModel.backgroundColor)
            }
        }
}
