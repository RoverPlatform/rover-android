package io.rover.rover.ui

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import io.rover.rover.ui.types.ViewType
import io.rover.rover.ui.viewmodels.LayoutableViewModel
import io.rover.rover.ui.views.ImageBlockView
import io.rover.rover.ui.views.LayoutableView
import io.rover.rover.ui.views.RectangleBlockView
import io.rover.rover.ui.views.RowView
import io.rover.rover.ui.views.TextBlockView

/**
 * This [RecyclerView.ViewHolder] wraps a [LayoutableViewModel].
 */
class LayoutableBlockHolder(
    private val layoutableItemView: LayoutableView<in LayoutableViewModel>,
    private val viewType: ViewType
) : RecyclerView.ViewHolder(
    layoutableItemView.view
) {
    var viewModel: LayoutableViewModel? = null
        set(value) {
            if (value != null) {

                layoutableItemView.viewModel = value
            }
            field = value
        }
}

class BlockAndRowRecyclerAdapter(
    private val viewModelSequence: List<LayoutableViewModel>
) : RecyclerView.Adapter<LayoutableBlockHolder>() {
    override fun getItemCount(): Int {
        return viewModelSequence.size
    }

    override fun onBindViewHolder(holder: LayoutableBlockHolder, position: Int) {
        holder.viewModel = viewModelSequence[position]
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LayoutableBlockHolder {
        val type = ViewType.values()[viewType]
        return LayoutableBlockHolder(
            viewFactory(parent, type),
            type
        )
    }

    override fun getItemViewType(position: Int): Int {
        val viewModel = viewModelSequence[position]
        return viewModel.viewType.ordinal
    }

    private fun viewFactory(parent: ViewGroup, viewType: ViewType): LayoutableView<in LayoutableViewModel> {
        return when (viewType) {
            ViewType.Row -> RowView(parent.context)
            ViewType.Rectangle -> RectangleBlockView(parent.context)
            ViewType.Text -> TextBlockView(parent.context)
            ViewType.Image -> ImageBlockView(parent.context)
        } as LayoutableView<LayoutableViewModel> // TODO: this cast is terrible
    }
}
