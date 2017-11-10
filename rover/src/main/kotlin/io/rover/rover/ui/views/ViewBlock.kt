package io.rover.rover.ui.views

import android.graphics.Rect
import android.view.View
import io.rover.rover.ui.types.dpAsPx
import io.rover.rover.ui.viewmodels.BlockViewModelInterface

class ViewBlock(
    private val view: View
): ViewBlockInterface {
    // State:
    override var blockViewModel: BlockViewModelInterface? = null
        set(viewModel) {
            field = viewModel
            val displayMetrics = view.resources.displayMetrics
            if(viewModel != null) {
                // zero out the contributed padding
                contributedPadding = Rect()
                refreshPadding()
            }

        }

    private var contributedPadding: Rect = Rect()

    override fun contributeAdditionalPadding(additionalPadding: Rect) {
        contributedPadding = Rect(
            contributedPadding.left + additionalPadding.left,
            contributedPadding.top + additionalPadding.top,
            contributedPadding.right + additionalPadding.right,
            contributedPadding.bottom + additionalPadding.bottom
        )
        refreshPadding()
    }

    private fun refreshPadding() {
        val displayMetrics = view.resources.displayMetrics
        val viewModel = blockViewModel
        if(viewModel != null) {
            view.setPaddingRelative(
                (viewModel.insets.left + contributedPadding.left).dpAsPx(displayMetrics),
                (viewModel.insets.top + contributedPadding.top).dpAsPx(displayMetrics),
                (viewModel.insets.right + contributedPadding.right).dpAsPx(displayMetrics),
                (viewModel.insets.bottom + contributedPadding.bottom).dpAsPx(displayMetrics)
            )
        }
    }
}
