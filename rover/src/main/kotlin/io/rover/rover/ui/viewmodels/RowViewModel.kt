package io.rover.rover.ui.viewmodels

import android.graphics.Rect
import android.graphics.RectF
import io.rover.rover.core.domain.RectangleBlock
import io.rover.rover.core.domain.Row
import io.rover.rover.ui.BlockViewModelFactory
import io.rover.rover.ui.BlockViewModelFactoryInterface
import io.rover.rover.ui.measuredAgainst
import io.rover.rover.ui.types.ViewType


class RowViewModel(
    private val row: Row,
    private val blockViewModelFactory: BlockViewModelFactoryInterface
) : RowViewModelInterface, BackgroundViewModelInterface by BackgroundViewModel(row) {
    override val viewType: ViewType = ViewType.Row

    override val blockViewModels: List<BlockViewModelInterface> by lazy {
        row.blocks.map { blockViewModelFactory.viewModelForBlock(it) }
    }

    /**
     * Returns the position (with origin being the bounds) that this view model should
     * be laid out.  Note that the returned rect is relative to the same space as the given [bounds]
     * rect, but not relative to the [bounds] rect itself.
     *
     * Also note that the [RectF.bottom] value of the [bounds] will be ignored; rows are entirely
     * responsible for defining their own heights, and are not height-constrained by the containing
     * [ScreenViewModel].
     */
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
            blockViewModels.map { it.stackedHeight(bounds) }.sum()
        } else {
            row.height.measuredAgainst(bounds.height())
        }
    }

    override fun mapBlocksToRectDisplayList(rowBounds: RectF): List<Pair<RectF, LayoutableViewModel>> {
        return mapBlocksToRectDisplayList(
            this.blockViewModels,
            rowBounds,
            0.0f,
            listOf()
        )
    }

    private tailrec fun mapBlocksToRectDisplayList(
        remainingBlockViewModels: List<BlockViewModelInterface>,
        rowBounds: RectF,
        accumulatedStackHeight: Float,
        results: List<Pair<RectF, LayoutableViewModel>>
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
