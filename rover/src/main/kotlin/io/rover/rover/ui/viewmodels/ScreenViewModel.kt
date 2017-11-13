package io.rover.rover.ui.viewmodels

import android.graphics.RectF
import io.rover.rover.core.domain.Screen
import io.rover.rover.ui.ViewModelFactoryInterface
import io.rover.rover.ui.types.DisplayItem
import io.rover.rover.ui.types.Layout

class ScreenViewModel(
    private val screen: Screen,
    private val viewModelFactory: ViewModelFactoryInterface
) : ScreenViewModelInterface {

    override fun rowViewModels(): List<RowViewModelInterface> {
        return screen.rows.map {
            viewModelFactory.viewModelForRow(it)
        }
    }

    override fun gather(): List<LayoutableViewModel> {
        return rowViewModels().flatMap {
            listOf(
                it
            ) + it.blockViewModels.asReversed()
        }
    }

    override fun render(
        widthDp: Float
    ): Layout =
        mapRowsToRectDisplayList(rowViewModels(), widthDp)

    private tailrec fun mapRowsToRectDisplayList(
        remainingRowViewModels: List<RowViewModelInterface>,
        width: Float,
        results: Layout = Layout(listOf(), 0f)
    ): Layout {
        if (remainingRowViewModels.isEmpty()) {
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

        val rowHead = listOf(DisplayItem(rowFrame, null, row))

        // Lay out the blocks, and then reverse the list to suit the requirement that *later* items
        // in the list must occlude prior ones.
        val blocks = row.mapBlocksToRectDisplayList(rowFrame).asReversed()

        return mapRowsToRectDisplayList(tail, width, Layout(results.coordinatesAndViewModels + rowHead + blocks, results.height + row.frame(rowBounds).height()))
    }
}
