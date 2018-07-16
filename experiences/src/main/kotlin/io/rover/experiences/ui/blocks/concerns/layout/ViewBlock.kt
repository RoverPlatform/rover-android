package io.rover.experiences.ui.blocks.concerns.layout

import android.view.MotionEvent
import android.view.View
import io.rover.core.ui.concerns.BindableView
import io.rover.core.ui.concerns.ViewModelBinding
import io.rover.core.ui.dpAsPx

class ViewBlock(
    override val view: View,
    private val paddingContributors: Set<PaddingContributor> = emptySet()
) : ViewBlockInterface {
    override var viewModel: BindableView.Binding<BlockViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->
        val viewModel = binding?.viewModel

        val displayMetrics = view.resources.displayMetrics

        view.setOnClickListener { viewModel?.click() }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> viewModel?.touched()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> viewModel?.released()
            }

            false
        }

        if (viewModel != null) {
            // TODO if I elect to implement `viewModel as LayoutPaddingDeflection` then I could
            // perhaps remove PaddingContributor (if there isn't something I'm forgetting).
            // Investigate this.
            val contributedPaddings = paddingContributors.map { it.contributedPadding }
            view.setPaddingRelative(
                (viewModel.insets.left + contributedPaddings.map { it.left }.sum()).dpAsPx(displayMetrics),
                (viewModel.insets.top + contributedPaddings.map { it.top }.sum()).dpAsPx(displayMetrics),
                (viewModel.insets.right + contributedPaddings.map { it.right }.sum()).dpAsPx(displayMetrics),
                (viewModel.insets.bottom + contributedPaddings.map { it.bottom }.sum()).dpAsPx(displayMetrics)
            )

            view.alpha = viewModel.opacity

            view.isClickable = viewModel.isClickable

            // TODO: figure out how to set a ripple drawable for clickable blocks in a way that
            // works across different view types?
        } else {
            view.setPaddingRelative(0, 0, 0, 0)
        }
    }
}
