package io.rover.rover.ui.viewmodels

import android.graphics.RectF
import io.rover.rover.core.domain.Row
import io.rover.rover.services.assets.AssetService
import io.rover.rover.ui.ViewModelFactoryInterface
import io.rover.rover.ui.measuredAgainst
import io.rover.rover.ui.types.DisplayItem
import io.rover.rover.ui.types.ViewType


class RowViewModel(
    private val row: Row,
    private val viewModelFactory: ViewModelFactoryInterface,
    private val backgroundViewModel: BackgroundViewModelInterface
) : RowViewModelInterface, BackgroundViewModelInterface by backgroundViewModel {
    override val viewType: ViewType = ViewType.Row

    override val blockViewModels: List<BlockViewModelInterface> by lazy {
        row.blocks.map { viewModelFactory.viewModelForBlock(it) }
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
        return if (row.autoHeight) {
            blockViewModels.map { it.stackedHeight(bounds) }.sum()
        } else {
            row.height.measuredAgainst(bounds.height())
        }
    }

    override fun mapBlocksToRectDisplayList(rowFrame: RectF): List<DisplayItem> {
        // kick off the iteration to statefully map (for stacking, as needed) the blocks into
        // a flat layout of coordinates.
        return mapBlocksToRectDisplayList(
            this.blockViewModels,
            rowFrame,
            0.0f,
            listOf()
        )
    }

    private tailrec fun mapBlocksToRectDisplayList(
        remainingBlockViewModels: List<BlockViewModelInterface>,
        rowFrame: RectF,
        accumulatedStackHeight: Float,
        results: List<DisplayItem>
    ): List<DisplayItem> {
        if (remainingBlockViewModels.isEmpty()) {
            return results
        }
        val block = remainingBlockViewModels.first()

        // if we're stacked, we need to stack on top of any prior stacked elements.
        val stackDeflection = if (block.isStacked) accumulatedStackHeight else 0.0f

        val blockBounds = RectF(
            rowFrame.left,
            rowFrame.top + stackDeflection,
            rowFrame.right,
            rowFrame.bottom + stackDeflection
        )
        val blockFrame = block.frame(blockBounds)

        val tail = remainingBlockViewModels.subList(1, remainingBlockViewModels.size)

        // if blockFrame exceeds the blockBounds, we need clip, and in terms relative to blockBounds
        val clip = if (!rowFrame.contains(blockFrame)) {
            // RectF has the functionality we need but has an imperative/mutation-style of API.
            RectF().apply {
                // this just copies blockBounds because intersect() mutates the instance.
                set(rowFrame)

                // and find the intersection with blockFrame to find out what should be exposed.
                intersect(blockFrame)

                // transform into coord space with origin of blockframe' top left corner
                this.offset(0 - blockFrame.left, 0 - blockFrame.top)
            }
        } else {
            // no clip is necessary because the blockFrame is contained entirely within the
            // surrounding block.
            null
        }

        return mapBlocksToRectDisplayList(
            tail,
            rowFrame,
            accumulatedStackHeight + block.stackedHeight(blockBounds),
            results + listOf(
                DisplayItem(blockFrame, clip, block)
            )
        )
    }
}
