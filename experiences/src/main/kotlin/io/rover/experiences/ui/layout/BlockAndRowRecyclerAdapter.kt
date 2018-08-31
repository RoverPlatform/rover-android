package io.rover.experiences.ui.layout

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.util.DisplayMetrics
import android.view.ViewGroup
import io.rover.core.ui.concerns.MeasuredBindableView
import io.rover.core.ui.concerns.MeasuredSize
import io.rover.core.ui.concerns.ViewModelBinding
import io.rover.experiences.ui.blocks.concerns.layout.LayoutableView
import io.rover.experiences.ui.blocks.concerns.layout.LayoutableViewModel

/**
 * The RecyclerView adapter for Experience layouts.
 */
open class BlockAndRowRecyclerAdapter(
    private val layout: Layout,
    private val displayMetrics: DisplayMetrics,
    private val blockViewFactory: (viewType: ViewType, context: Context) -> LayoutableView<LayoutableViewModel>
) : RecyclerView.Adapter<BlockAndRowRecyclerAdapter.LayoutableBlockHolder>() {

    private val viewModels = layout.coordinatesAndViewModels.map { it.viewModel }

    override fun getItemCount(): Int {
        return viewModels.size
    }

    override fun onBindViewHolder(holder: LayoutableBlockHolder, position: Int) {
        holder.viewModel = MeasuredBindableView.Binding(
            viewModels[position],
            MeasuredSize(
                layout.coordinatesAndViewModels[position].position.width(),
                layout.coordinatesAndViewModels[position].position.height(),
                displayMetrics.density
            )
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LayoutableBlockHolder {
        val type = ViewType.values()[viewType]
        return LayoutableBlockHolder(
            viewFactory(parent, type)
        )
    }

    override fun getItemViewType(position: Int): Int {
        val viewModel = viewModels[position]
        return viewModel.viewType.ordinal
    }

    private fun viewFactory(parent: ViewGroup, viewType: ViewType): LayoutableView<LayoutableViewModel> {
        return blockViewFactory(viewType, parent.context)
    }

    /**
     * This [RecyclerView.ViewHolder] wraps a [LayoutableViewModel].
     */
    class LayoutableBlockHolder(
        private val layoutableItemView: LayoutableView<in LayoutableViewModel>
    ) : RecyclerView.ViewHolder(
        layoutableItemView.view
    ) {
        var viewModel: MeasuredBindableView.Binding<LayoutableViewModel>? by ViewModelBinding { binding, _ ->
            layoutableItemView.viewModelBinding = binding
        }
    }
}
