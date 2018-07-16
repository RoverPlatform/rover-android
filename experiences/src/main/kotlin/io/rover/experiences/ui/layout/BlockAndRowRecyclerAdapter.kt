package io.rover.experiences.ui.layout

import android.support.v7.widget.RecyclerView
import android.util.DisplayMetrics
import android.view.ViewGroup
import io.rover.experiences.ui.blocks.barcode.BarcodeBlockView
import io.rover.experiences.ui.blocks.button.ButtonBlockView
import io.rover.experiences.ui.blocks.concerns.layout.LayoutableView
import io.rover.experiences.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.experiences.ui.blocks.image.ImageBlockView
import io.rover.experiences.ui.blocks.rectangle.RectangleBlockView
import io.rover.experiences.ui.blocks.text.TextBlockView
import io.rover.experiences.ui.blocks.web.WebBlockView
import io.rover.experiences.ui.layout.row.RowView
import io.rover.core.ui.concerns.BindableView
import io.rover.core.ui.concerns.MeasuredSize
import io.rover.core.ui.concerns.ViewModelBinding

/**
 * The RecyclerView adapter for Experience layouts.
 */
open class BlockAndRowRecyclerAdapter(
    private val layout: Layout,
    private val displayMetrics: DisplayMetrics
) : RecyclerView.Adapter<BlockAndRowRecyclerAdapter.LayoutableBlockHolder>() {

    private val viewModels = layout.coordinatesAndViewModels.map { it.viewModel }

    override fun getItemCount(): Int {
        return viewModels.size
    }

    override fun onBindViewHolder(holder: LayoutableBlockHolder, position: Int) {
        holder.viewModel = BindableView.Binding(
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
        // We are instantiating the row views here, but we cannot emit them with `out` variance
        // because the recyclerview Holder must be able to set their view models, which are always
        // subtypes.
        @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
        return when (viewType) {
            ViewType.Row -> RowView(parent.context)
            ViewType.Rectangle -> RectangleBlockView(parent.context)
            ViewType.Text -> TextBlockView(parent.context)
            ViewType.Image -> ImageBlockView(parent.context)
            ViewType.Button -> ButtonBlockView(parent.context)
            ViewType.WebView -> WebBlockView(parent.context)
            ViewType.Barcode -> BarcodeBlockView(parent.context)
        } as LayoutableView<LayoutableViewModel>

        // TODO: should we delegate this to the Experiences DI container?
    }

    /**
     * This [RecyclerView.ViewHolder] wraps a [LayoutableViewModel].
     */
    class LayoutableBlockHolder(
        private val layoutableItemView: LayoutableView<in LayoutableViewModel>
    ) : RecyclerView.ViewHolder(
        layoutableItemView.view
    ) {
        var viewModel: BindableView.Binding<LayoutableViewModel>? by ViewModelBinding { binding, _ ->
            layoutableItemView.viewModel = binding
        }
    }
}
