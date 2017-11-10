package io.rover.rover.ui.views

import android.graphics.Rect
import android.view.View
import io.rover.rover.ui.types.dpAsPx
import io.rover.rover.ui.viewmodels.BlockViewModelInterface

class ViewBlock(
    private val view: View,
    private val paddingContributors: Set<PaddingContributor> = emptySet()
): ViewBlockInterface {
    // State:
    override var blockViewModel: BlockViewModelInterface? = null
        set(viewModel) {
            field = viewModel
            val displayMetrics = view.resources.displayMetrics
            if(viewModel != null) {
                val contributedPaddings = paddingContributors.map { it.contributedPadding }
                view.setPaddingRelative(
                    (viewModel.insets.left + contributedPaddings.map { it.left }.sum()).dpAsPx(displayMetrics),
                    (viewModel.insets.top + contributedPaddings.map { it.top }.sum()).dpAsPx(displayMetrics),
                    (viewModel.insets.right + contributedPaddings.map { it.right }.sum()).dpAsPx(displayMetrics),
                    (viewModel.insets.bottom + contributedPaddings.map { it.bottom }.sum()).dpAsPx(displayMetrics)
                )
            } else {
                view.setPaddingRelative(0, 0,0, 0)
            }

        }
}
