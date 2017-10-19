package io.rover.rover.ui.viewmodels

import android.graphics.RectF
import io.rover.rover.core.domain.RectangleBlock
import io.rover.rover.core.domain.Row
import io.rover.rover.ui.measuredAgainst
import io.rover.rover.ui.types.ViewType


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

    override fun frame(bounds: RectF): RectF {
        val x = bounds.left
        val y = bounds.top
        val width = bounds.width()
        val height = height(bounds)

        return RectF(
            x,
            y,
            x + width,
            y + height
        )
    }

    private fun height(bounds: RectF): Float {
        return if(row.autoHeight) {
            blockViewModels().map { it.stackedHeight(bounds) }.sum()
        } else {
            row.height.measuredAgainst(bounds.height())
        }
    }
}
