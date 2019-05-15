package io.rover.sdk.ui.layout.screen

import io.rover.sdk.data.domain.Attributes
import io.rover.sdk.logging.log
import io.rover.sdk.streams.asPublisher
import io.rover.sdk.streams.flatMap
import io.rover.sdk.streams.map
import io.rover.sdk.data.domain.Row
import io.rover.sdk.data.domain.Screen
import io.rover.sdk.data.domain.TitleBarButtons
import io.rover.sdk.ui.RectF
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.sdk.ui.layout.DisplayItem
import io.rover.sdk.ui.layout.Layout
import io.rover.sdk.ui.layout.row.RowViewModelInterface
import io.rover.sdk.ui.toolbar.ToolbarConfiguration
import org.reactivestreams.Publisher

internal class ScreenViewModel(
    override val screen: Screen,
    private val backgroundViewModel: BackgroundViewModelInterface,
    private val resolveRowViewModel: (row: Row) -> RowViewModelInterface
) : ScreenViewModelInterface, BackgroundViewModelInterface by backgroundViewModel {

    // TODO: remember (State) scroll position

    private val rowsById: Map<String, Row> = screen.rows.associateBy { it.id }.let { rowsById ->
        if (rowsById.size != screen.rows.size) {
            log.w("Duplicate screen IDs appeared in screen $screenId.")
            emptyMap()
        } else rowsById
    }

    /**
     * Map of Row View models and the Row ids they own.  Map entry order is their order in the
     * Experience.
     */
    private val rowViewModelsById: Map<String, RowViewModelInterface> by lazy {
        rowsById.mapValues { (_, row) ->
            // TODO: why on earth is this copy() here?
            resolveRowViewModel(row.copy(blocks = row.blocks))
        }
    }

    override val rowViewModels by lazy {
        rowViewModelsById.values.toList()
    }

    override fun gather(): List<LayoutableViewModel> {
        return rowViewModels.flatMap { rowViewModel ->
            listOf(
                rowViewModel
            ) + rowViewModel.blockViewModels.asReversed()
        }
    }

    override val events: Publisher<ScreenViewModelInterface.Event> by lazy {
        rowViewModelsById.entries.asPublisher().flatMap { (rowId, rowViewModel) ->
            val row = rowsById[rowId]!!
            rowViewModel.eventSource.map { rowEvent ->
                ScreenViewModelInterface.Event(
                    rowId,
                    rowEvent.blockId,
                    rowEvent.navigateTo,
                    row
                )
            }
        }
    }

    override val needsBrightBacklight: Boolean by lazy {
        rowViewModels.any { it.needsBrightBacklight }
    }

    override val appBarConfiguration: ToolbarConfiguration
        get() = ToolbarConfiguration(
            screen.titleBar.useDefaultStyle,
            screen.titleBar.text,
            screen.titleBar.backgroundColor.asAndroidColor(),
            screen.titleBar.textColor.asAndroidColor(),
            screen.titleBar.buttonColor.asAndroidColor(),
            screen.titleBar.buttons == TitleBarButtons.Both || screen.titleBar.buttons == TitleBarButtons.Back,
            screen.titleBar.buttons == TitleBarButtons.Both || screen.titleBar.buttons == TitleBarButtons.Close,
            screen.statusBar.color.asAndroidColor()
        )

    override fun render(
        widthDp: Float
    ): Layout =
        mapRowsToRectDisplayList(rowViewModels, widthDp)

    private tailrec fun mapRowsToRectDisplayList(
        remainingRowViewModels: List<RowViewModelInterface>,
        width: Float,
        results: Layout = Layout(listOf(), 0f, width)
    ): Layout {
        if (remainingRowViewModels.isEmpty()) {
            return results
        }

        val rowViewModel = remainingRowViewModels.first()

        val rowBounds = RectF(
            0f,
            results.height,
            width,
            // the bottom value of the bounds is not used; the rows expand themselves as defined
            // or needed by autoheight content.
            0.0f
        )

        val rowFrame = rowViewModel.frame(rowBounds)

        val tail = remainingRowViewModels.subList(1, remainingRowViewModels.size)

        val rowHead = listOf(DisplayItem(rowFrame, null, rowViewModel))

        // Lay out the blocks, and then reverse the list to suit the requirement that *later* items
        // in the list must occlude prior ones.
        val blocks = rowViewModel.mapBlocksToRectDisplayList(rowFrame).asReversed()

        return mapRowsToRectDisplayList(
            tail,
            width,
            Layout(
                results.coordinatesAndViewModels + rowHead + blocks,
                results.height + rowViewModel.frame(rowBounds).height(),
                results.width
            )
        )
    }

    override val screenId: String
        get() = screen.id
}
