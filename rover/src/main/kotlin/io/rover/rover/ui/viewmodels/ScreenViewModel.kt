package io.rover.rover.ui.viewmodels

import android.graphics.Rect
import android.graphics.RectF
import io.rover.rover.core.domain.Screen
import io.rover.rover.core.logging.log
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

    override fun gather(): List<LayoutableViewModel> {
        return screen.rows.flatMap {
            val rowViewModel = RowViewModel(it)

            listOf(
                rowViewModel
            ) + rowViewModel.blockViewModels()
        }
    }

    override fun render(
        widthDp: Float
    ): Layout =
        mapRowsToRectDisplayList(screen.rows.map { RowViewModel(it) }, widthDp)

    private tailrec fun mapRowsToRectDisplayList(
        remainingRowViewModels: List<RowViewModelInterface>,
        width: Float,
        results: Layout = Layout(listOf(), 0f)
    ): Layout {
        if(remainingRowViewModels.isEmpty()) {
            return results
        }

        val row = remainingRowViewModels.first()

        val rowBounds = RectF(
            0f,
            results.height,
            width,
            // the bottom value of the bounds is not used; the rows expand themselves as defined
            // or needed by autoheight content.
            0.0f
        )

        val rowFrame = row.frame(rowBounds)

        val tail = remainingRowViewModels.subList(1, remainingRowViewModels.size)

        val rowHead = listOf(Pair(rowFrame, row))

        val blocks = mapBlocksToRectDisplayList(row.blockViewModels(), rowFrame, 0.0f)

        return mapRowsToRectDisplayList(tail, width, Layout(results.coordinatesAndViewModels + rowHead + blocks, results.height + row.frame(rowBounds).height()))
    }

    private tailrec fun mapBlocksToRectDisplayList(
        remainingBlockViewModels: List<BlockViewModelInterface>,
        rowBounds: RectF,
        accumulatedStackHeight: Float,
        results: List<Pair<RectF, LayoutableViewModel>> = listOf()
    ): List<Pair<RectF, LayoutableViewModel>> {
        if (remainingBlockViewModels.isEmpty()) {
            return results
        }
        val block = remainingBlockViewModels.first()

        // if we're stacked, we need to stack on top of any prior stacked elements.
        val stackDeflection = if (block.isStacked) accumulatedStackHeight else 0.0f

        val blockBounds = RectF(
            rowBounds.left,
            rowBounds.top + stackDeflection,
            rowBounds.right,
            rowBounds.bottom + stackDeflection
        )
        val blockFrame = block.frame(blockBounds)

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
