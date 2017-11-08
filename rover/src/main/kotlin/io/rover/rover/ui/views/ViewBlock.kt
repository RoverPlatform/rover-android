package io.rover.rover.ui.views

import android.view.View
import io.rover.rover.ui.types.dpAsPx
import io.rover.rover.ui.viewmodels.BlockViewModelInterface

class ViewBlock(
    private val view: View
): ViewBlockInterface {
    override var blockViewModel: BlockViewModelInterface? = null
        set(viewModel) {
            val displayMetrics = view.resources.displayMetrics
            if(viewModel != null) {
                view.setPaddingRelative(
                    viewModel.insets.left.dpAsPx(displayMetrics),
                    viewModel.insets.top.dpAsPx(displayMetrics),
                    viewModel.insets.right.dpAsPx(displayMetrics),
                    viewModel.insets.bottom.dpAsPx(displayMetrics)
                )
            }
        }
}
