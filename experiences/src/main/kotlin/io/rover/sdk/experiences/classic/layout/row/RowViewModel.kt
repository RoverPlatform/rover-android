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

package io.rover.sdk.experiences.classic.layout.row

import io.rover.sdk.core.data.domain.Block
import io.rover.sdk.core.data.domain.Height
import io.rover.sdk.core.data.domain.Row
import io.rover.sdk.core.streams.asPublisher
import io.rover.sdk.core.streams.filterNulls
import io.rover.sdk.core.streams.flatMap
import io.rover.sdk.core.streams.map
import io.rover.sdk.core.streams.share
import io.rover.sdk.experiences.classic.RectF
import io.rover.sdk.experiences.classic.blocks.barcode.BarcodeBlockViewModel
import io.rover.sdk.experiences.classic.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.experiences.classic.blocks.concerns.layout.BlockViewModelInterface
import io.rover.sdk.experiences.classic.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.sdk.experiences.classic.layout.DisplayItem
import io.rover.sdk.experiences.classic.layout.ViewType
import io.rover.sdk.experiences.classic.layout.screen.ScreenViewModel
import org.reactivestreams.Publisher

internal class RowViewModel(
    private val row: Row,
    private val blockViewModelResolver: (block: Block) -> CompositeBlockViewModelInterface,
    private val backgroundViewModel: BackgroundViewModelInterface
) : RowViewModelInterface, BackgroundViewModelInterface by backgroundViewModel {
    override val viewType: ViewType = ViewType.Row

    override val blockViewModels: List<BlockViewModelInterface> by lazy {
        row.blocks.map { blockViewModelResolver(it) }
    }

    override val eventSource: Publisher<RowViewModelInterface.Event> = blockViewModels.map { blockViewModel ->
        blockViewModel.events.map {
            when (it) {
                is BlockViewModelInterface.Event.Clicked -> RowViewModelInterface.Event(
                    it.blockId,
                    it.navigateTo
                )
                is BlockViewModelInterface.Event.Touched, is BlockViewModelInterface.Event.Released -> null
            }
        }.filterNulls()
    }.asPublisher().flatMap { it }.share()

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
        return when (row.height) {
            is Height.Intrinsic -> blockViewModels.map { it.stackedHeight(bounds) }.sum()
            is Height.Static -> ((row.height) as Height.Static).value.toFloat()
        }
    }

    override fun mapBlocksToRectDisplayList(rowFrame: RectF): List<DisplayItem> {
        // kick off the iteration to map (for stacking, as needed) the blocks into
        // a flat layout of coordinates while accumulating the stack height.
        return mapBlocksToRectDisplayList(
            this.blockViewModels,
            rowFrame,
            0.0f,
            listOf()
        )
    }

    override val needsBrightBacklight: Boolean by lazy {
        blockViewModels.any { blockViewModel -> blockViewModel is BarcodeBlockViewModel }
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

        // if blockFrame exceeds the rowFrame, we need clip, and in terms relative to blockBounds
        val clip = if (!rowFrame.contains(blockFrame)) {
            // and find the intersection with blockFrame to find out what should be exposed and then
            // transform into coordinate space with origin of the blockframe in the top left corner:
            val intersection = rowFrame.intersection(blockFrame)

            // translate the clip to return the intersection, but if there is none that means the
            // block is *entirely* outside of the bounds.  An unlikely but not impossible situation.
            // Clip it entirely:
            intersection?.offset(0 - blockFrame.left, 0 - blockFrame.top)
                ?: RectF(blockFrame.left, blockFrame.top, blockFrame.left, blockFrame.top)
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
