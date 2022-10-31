package io.rover.campaigns.experiences.ui.blocks.concerns.layout

import android.view.MotionEvent
import android.view.View
import io.rover.campaigns.experiences.ui.concerns.MeasuredBindableView
import io.rover.campaigns.experiences.ui.concerns.ViewModelBinding
import io.rover.campaigns.experiences.ui.dpAsPx

internal class ViewBlock(
    override val view: View
) : ViewBlockInterface {
    override var viewModelBinding: MeasuredBindableView.Binding<BlockViewModelInterface>? by ViewModelBinding { binding, _ ->
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
            view.setPaddingRelative(
                (viewModel.padding.left).dpAsPx(displayMetrics),
                (viewModel.padding.top).dpAsPx(displayMetrics),
                (viewModel.padding.right).dpAsPx(displayMetrics),
                (viewModel.padding.bottom).dpAsPx(displayMetrics)
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
