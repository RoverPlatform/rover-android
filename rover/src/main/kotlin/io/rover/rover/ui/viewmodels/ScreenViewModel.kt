package io.rover.rover.ui.viewmodels

import android.graphics.Rect
import io.rover.rover.core.domain.Screen
import io.rover.rover.ui.types.Layout

class ScreenViewModel(
    private val screen: Screen
): ScreenViewModelInterface {

    override fun rowViewModels(): List<RowViewModelInterface> {
        return screen.rows.map {
            RowViewModel(
                it
            )
        }
    }

    override fun render(
        widthDp: Int
    ): Layout =
        mapRowsToRectDisplayList(screen.rows.map { RowViewModel(it) }, widthDp)

    private tailrec fun mapRowsToRectDisplayList(
        remainingRowViewModels: List<RowViewModelInterface>,
        width: Int,
        results: Layout = Layout(listOf(), 0)
    ): Layout {
        // height is given as 0 here.  Might be OK, but...
        // ... TODO: if autoheight is not set, and row height is set as proportional (percentage) it will collapse to a height of 0.  However, not clear what behaviour would be expected in that case anyway.
        if(remainingRowViewModels.isEmpty()) {
            return results
        }
        val rowBounds = Rect(0, results.height, width, 0)

        val row = remainingRowViewModels.first()

        val rowFrame = row.frame(rowBounds)

        val tail = remainingRowViewModels.subList(1, remainingRowViewModels.size)

        val rowHead = listOf(Pair(rowFrame, row))

        val blocks = mapBlocksToRectDisplayList(row.blockViewModels(), rowBounds, 0.0f)

        return mapRowsToRectDisplayList(tail, width, Layout(results.coordinatesAndViewModels + rowHead + blocks, results.height + row.frame(rowBounds).height()))
    }

    private tailrec fun mapBlocksToRectDisplayList(
        remainingBlockViewModels: List<BlockViewModelInterface>,
        rowBounds: Rect,
        accumulatedStackHeight: Float,
        results: List<Pair<Rect, LayoutableViewModel>> = listOf()
    ): List<Pair<Rect, LayoutableViewModel>> {
        if (remainingBlockViewModels.isEmpty()) {
            return results
        }
        val block = remainingBlockViewModels.first()

        // if we're stacked, we need to stack on top of any prior stacked elements.
        val stackDeflection = if (block.isStacked) accumulatedStackHeight else 0.0f

        val blockBounds = Rect(
            rowBounds.left,
            rowBounds.top + stackDeflection.toInt(),
            rowBounds.right,
            rowBounds.bottom + stackDeflection.toInt()
        )
        val blockFrame = block.frame(rowBounds)

        val tail = remainingBlockViewModels.subList(1, remainingBlockViewModels.size)

        return mapBlocksToRectDisplayList(
            tail,
            rowBounds,
            accumulatedStackHeight + block.stackedHeight(blockBounds),
            results + listOf(
                Pair(blockFrame, block)
            )
        )
    }
}
