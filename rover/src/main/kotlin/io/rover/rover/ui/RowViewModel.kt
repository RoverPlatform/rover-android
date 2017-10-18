package io.rover.rover.ui

import android.graphics.Rect
import io.rover.rover.core.domain.RectangleBlock
import io.rover.rover.core.domain.Row


class RowViewModel(
    private val row: Row
) : RowViewModelInterface, BackgroundViewModelInterface by BackgroundViewModel(row) {
    override val viewType: ViewType = ViewType.Row

    override fun blockViewModels(): List<BlockViewModelInterface> {
        return row.blocks.map {
            // TODO: type mapping goes here
            RectangleBlockViewModel(
                it as RectangleBlock // TODO only supporting RectangleBlock for now
            )
        }
    }

    override fun frame(bounds: Rect): Rect {
        val x = bounds.left
        val y = bounds.top
        val width = bounds.width()
        val height = height(bounds)

        return Rect(
            x,
            y,
            x + width,
            y + height.toInt() // TODO: the right rounding/place for this type coercion?
        )
    }

    private fun height(bounds: Rect): Float {
        return if(row.autoHeight) {
            blockViewModels().map { it.stackedHeight(bounds) }.sum()
        } else {
            row.height.measuredAgainst(bounds.height().toFloat())
        }
    }
}
