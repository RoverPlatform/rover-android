/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.experiences.classic.layout

import android.content.Context
import android.util.DisplayMetrics
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.rover.sdk.experiences.classic.blocks.concerns.layout.LayoutableView
import io.rover.sdk.experiences.classic.blocks.concerns.layout.LayoutableViewModel
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView
import io.rover.sdk.experiences.classic.concerns.MeasuredSize
import io.rover.sdk.experiences.classic.concerns.ViewModelBinding

/**
 * The RecyclerView adapter for Experience layouts.
 */
internal class BlockAndRowRecyclerAdapter(
    private val layout: Layout,
    private val displayMetrics: DisplayMetrics,
    private val blockViewFactory: (viewType: ViewType, context: Context) -> LayoutableView<out LayoutableViewModel>
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

        // we know that viewFactory is returning a LayoutableView that *exactly* matches the
        // viewType, so we can work around the variance problem with an unchecked cast.  There isn't
        // a way to express this with the type system (because of switching on ViewType at runtime,
        // which is outside the knowledge of the type system), so we have to count on this invariant
        // ourselves.
        @Suppress("UNCHECKED_CAST")
        return LayoutableBlockHolder(
            viewFactory(parent, type) as LayoutableView<LayoutableViewModel>
        )
    }

    override fun getItemViewType(position: Int): Int {
        val viewModel = viewModels[position]
        return viewModel.viewType.ordinal
    }

    private fun viewFactory(parent: ViewGroup, viewType: ViewType): LayoutableView<out LayoutableViewModel> {
        // we have a little bit of trouble with variance here. Basically, the
//        @Suppress("UNCHECKED_CAST")
        return blockViewFactory(viewType, parent.context) // as LayoutableView<LayoutableViewModel>
    }

    /**
     * This [RecyclerView.ViewHolder] wraps a [LayoutableViewModel].
     */
    class LayoutableBlockHolder(
        private val layoutableItemView: LayoutableView<LayoutableViewModel>
    ) : RecyclerView.ViewHolder(
        layoutableItemView.view
    ) {
        var viewModel: MeasuredBindableView.Binding<LayoutableViewModel>? by ViewModelBinding { binding, _ ->
            layoutableItemView.viewModelBinding = binding
        }
    }
}
