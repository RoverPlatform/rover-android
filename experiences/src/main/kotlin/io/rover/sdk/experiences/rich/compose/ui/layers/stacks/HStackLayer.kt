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

package io.rover.sdk.experiences.rich.compose.ui.layers.stacks

import android.os.Trace
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.rover.sdk.experiences.rich.compose.model.nodes.HStack
import io.rover.sdk.experiences.rich.compose.model.values.Alignment
import io.rover.sdk.experiences.rich.compose.model.values.Axis
import io.rover.sdk.experiences.rich.compose.model.values.ColorReference
import io.rover.sdk.experiences.rich.compose.model.values.Fill
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.layers.ApplyLayerModifiers
import io.rover.sdk.experiences.rich.compose.ui.layers.Children
import io.rover.sdk.experiences.rich.compose.ui.layers.RectangleLayer
import io.rover.sdk.experiences.rich.compose.ui.layout.annotateIntrinsicsCrash
import io.rover.sdk.experiences.rich.compose.ui.layout.fallbackMeasure
import io.rover.sdk.experiences.rich.compose.ui.layout.mapMaxIntrinsicWidthAsMeasure
import io.rover.sdk.experiences.rich.compose.ui.layout.mapMinIntrinsicAsFlex
import io.rover.sdk.experiences.rich.compose.ui.modifiers.LayerModifiers
import io.rover.sdk.experiences.rich.compose.ui.layout.ifInfinity

@Composable
internal fun HStackLayer(node: HStack, modifier: Modifier = Modifier) {
    HStackLayer(spacing = node.spacing, alignment = node.alignment, layerModifiers = LayerModifiers(node), modifier = modifier) {
        Children(children = node.children, Modifier)
    }
}

@Composable
internal fun HStackLayer(
    modifier: Modifier = Modifier,
    spacing: Float = 10f,
    alignment: Alignment = Alignment.CENTER,
    layerModifiers: LayerModifiers = LayerModifiers(),
    content: @Composable () -> Unit,
) {
    val localDensityContext = LocalDensity.current

    val spacingAsPx = with(localDensityContext) {
        spacing.dp.roundToPx()
    }

    ApplyLayerModifiers(layerModifiers, modifier = modifier) { modifier ->
        CompositionLocalProvider(Environment.LocalStackAxis provides Axis.HORIZONTAL) {
            Layout(content, measurePolicy = hStackMeasurePolicy(spacingAsPx, alignment), modifier = modifier)
        }
    }
}

/**
 * Measure policy for the HStackLayer.
 * It overrides the intrinsic width functions to remove the default behavior and better handle
 * infinity in children widths.
 */
internal fun hStackMeasurePolicy(spacingAsPx: Int, alignment: Alignment): MeasurePolicy {
    val tag = "HStackMeasurePolicy"
    return object : MeasurePolicy {

        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult {
            if (measurables.isEmpty()) {
                return layout(0, 0) { }
            }

            Trace.beginSection("HStackLayer::measure")

            val groupedMeasurables = measurables.groupByHorizontalFlexibilityAndPriority()

            val proposedSize = Size(constraints.maxWidth, constraints.maxHeight)

            val proposedSizeWithFallback = computeCrossAxisFallbackIfNeeded(
                proposedSize,
                groupedMeasurables,
                spacingAsPx,
            )

            val measurableAndPlaceablePairs = groupedMeasurables.measureOrderedHStackChildren(
                proposedSizeWithFallback,
                spacingAsPx,
            )

            // Placement step:

            // reorder the placeables returned by measureOrderedHStackChildren to match the original order of the measurables:
            val placeableLookup = measurableAndPlaceablePairs.associate {
                it.first to it.second
            }
            val placeables = measurables.mapNotNull { measurable ->
                // now get the placeable for this measurable, and yield it.
                placeableLookup[measurable]
            }

            val maxHeight = placeables.maxOf { it.measuredHeight }
            val width = placeables.sumOfWithLayoutSpacing(spacingAsPx) { it.measuredWidth }
            val l = layout(width, maxHeight) {
                var xPosition = 0

                placeables.forEach { placeable ->
                    val yPosition = when (alignment) {
                        Alignment.TOP -> 0
                        Alignment.BOTTOM -> maxHeight - placeable.measuredHeight
                        // TODO: This alignment type has its own issue: https://github.com/judoapp/judo-android-develop/issues/636
                        Alignment.FIRST_TEXT_BASELINE -> 0
                        // The default Alignment for HStack is CENTER.
                        else -> maxOf(maxHeight / 2 - placeable.measuredHeight / 2, 0)
                    }

                    placeable.placeRelative(
                        x = (placeable.measuredWidth - placeable.width) / 2 + xPosition,
                        y = (placeable.measuredHeight - placeable.height) / 2 + yPosition,
                    )
                    xPosition += placeable.measuredWidth + spacingAsPx
                }
            }
            Trace.endSection()
            return l
        }

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int {
            Trace.beginSection("HStackLayer::intrinsicMeasure")
            try {
                return mapMaxIntrinsicWidthAsMeasure(height) { proposedSize ->
                    if (measurables.isEmpty()) {
                        return@mapMaxIntrinsicWidthAsMeasure Size(0, 0)
                    }

                    val groupedMeasurables = measurables.groupByHorizontalFlexibilityAndPriority()

                    val proposedSizeWithFallback = computeCrossAxisFallbackIfNeeded(
                        proposedSize,
                        groupedMeasurables,
                        spacingAsPx
                    )

                    val measureResults = groupedMeasurables.measureOrderedHStackChildrenUsingIntrinsics(
                        proposedSizeWithFallback,
                        spacingAsPx,
                    )

                    val finalChildSizes = measureResults.values

                    val maxHeight = finalChildSizes.maxOf { it.height }
                    val width = finalChildSizes.sumOfWithLayoutSpacing(spacingAsPx) { it.width }

                    Size(width, maxHeight)
                }
            } finally {
                Trace.endSection()
            }
        }

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int {
            Trace.beginSection("HStackLayer::intrinsicMeasure::horizontalFlex")
            return try {
                mapMinIntrinsicAsFlex {
                    primaryAxisFlex(Axis.HORIZONTAL, measurables, spacingAsPx)
                }
            } finally {
                Trace.endSection()
            }
        }

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int {
            Trace.beginSection("HStackLayer::intrinsicMeasure::verticalFlex")
            return try {
                mapMinIntrinsicAsFlex {
                    crossAxisFlex(Axis.HORIZONTAL, measurables)
                }
            } finally {
                Trace.endSection()
            }
        }

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int {
            throw IllegalStateException("Only call the Rover overloaded packed intrinsics methods on Rover measurables, maxIntrinsicHeight is not used")
        }
    }
}

@Preview
@Composable
private fun OfferedInfinityWidth() {
    // the scroll container will offer the stack infinity width.  We want to test this use case
    // explicitly without depending on ScrollContainerLayer (which itself has an implicit VStack in
    // it, making for an awkward test situation)

    Layout({
        HStackLayer() {
            // these rectangles should be offered infinity on width, and ExpandMeasurePolicy
            // should fall back to 10 dp.
            RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("blue")))
            RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("red")))
        }
    }, measurePolicy = InfiniteWidthMeasurePolicy)
}

private object InfiniteWidthMeasurePolicy : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val childConstraints = constraints.copy(
            maxWidth = Constraints.Infinity,
        )
        val placeables = measurables.map { it.measure(childConstraints) }

        return layout(placeables.maxOf { it.measuredWidth }, placeables.maxOf { it.measuredHeight }) {
            placeables.forEach {
                it.placeRelative(0, 0)
            }
        }
    }
}

private fun <T : IntrinsicMeasurable> Collection<Collection<MeasurableWithSortInfo<T>>>.measureOrderedHStackChildrenUsingIntrinsics(
    proposedSize: Size,
    spacingAsPx: Int,
): Map<IntrinsicMeasurable, Size> {
    val totalChildren = this.sumOf { it.count() }
    val totalSpacing = maxOf(
        (spacingAsPx * (totalChildren - 1)),
        0,
    )
    var remainingWidth =
        if (proposedSize.width == Constraints.Infinity) {
            Constraints.Infinity
        } else {
            proposedSize.width - totalSpacing
        }

    val childSizes = hashMapOf<IntrinsicMeasurable, Size>()

    // this is a mutable set of all remaining children to be measured, in order to obtain
    // their flex minimums.
    val remainingFlexMinimumChildren = this.flatten().toMutableSet()

    forEachIndexed { priorityGroupIndex, measurables ->
        measurables.forEachIndexed { measurableIndex, layoutInfo ->
            layoutInfo.measurable.annotateIntrinsicsCrash {
                if (remainingWidth == Constraints.Infinity) {
                    val childSize = layoutInfo.measurable.fallbackMeasure(
                        Size(
                            Constraints.Infinity,
                            proposedSize.height,
                        ),
                    )
                    childSizes[layoutInfo.measurable] = childSize
                } else {
                    remainingFlexMinimumChildren.removeIf { it.measurable == layoutInfo.measurable }
                    val remainingMinimum = remainingFlexMinimumChildren.map { it.flexRange.first }.filter { it != Constraints.Infinity}.sum()

                    val remainingChildren = measurables.size - measurableIndex
                    val proposeToChild = maxOf((remainingWidth - remainingMinimum) / remainingChildren, 0)

                    val childSize = layoutInfo.measurable.fallbackMeasure(
                        Size(
                            proposeToChild,
                            proposedSize.height,
                        ),
                    )

                    remainingWidth -= childSize.width

                    childSizes[layoutInfo.measurable] = childSize
                }
            }
        }
    }

    return childSizes
}

private fun Collection<Collection<MeasurableWithSortInfo<Measurable>>>.measureOrderedHStackChildren(
    proposedSize: Size,
    spacingAsPx: Int,
): List<Pair<Measurable, Placeable>> {
    val totalChildren = this.sumOf { it.count() }
    val totalSpacing = maxOf(
        (spacingAsPx * (totalChildren - 1)),
        0,
    )
    var remainingWidth =
        if (proposedSize.width == Constraints.Infinity) {
            Constraints.Infinity
        } else {
            proposedSize.width - totalSpacing
        }

    // this is a mutable set of all remaining children to be measured, in order to obtain
    // their flex minimums.
    val remainingFlexMinimumChildren = this.flatten().toMutableSet()

    val measurableAndPlaceables = flatMap { measurables ->
        measurables.mapIndexed { index, layoutInfo ->
            layoutInfo.measurable.annotateIntrinsicsCrash {
                if (remainingWidth == Constraints.Infinity) {
                    Pair(
                        layoutInfo.measurable,
                        layoutInfo.measurable.measure(
                            Constraints(
                                maxWidth = Constraints.Infinity,
                                maxHeight = proposedSize.height,
                            ),
                        ),
                    )
                } else {
                    val remainingChildren: Int = measurables.size - index
                    remainingFlexMinimumChildren.removeIf { it.measurable == layoutInfo.measurable }

                    // obtain the minimums that must be set aside for lower priority children.
                    val remainingMinimum = remainingFlexMinimumChildren
                            // filter for only the lower priority children
                            .filter { it.priority < layoutInfo.priority }
                            // take the minimum bound of flex range (excluding infinity)
                            .map { it.flexRange.first }.filter { it != Constraints.Infinity}.sum()
                    val proposeToChild = maxOf((remainingWidth - remainingMinimum) / remainingChildren, 0)

                    val placeable = layoutInfo.measurable.measure(
                        Constraints(
                            maxWidth = proposeToChild,
                            maxHeight = proposedSize.height,
                        ),
                    )

                    remainingWidth -= placeable.measuredWidth

                    Pair(layoutInfo.measurable, placeable)
                }
            }
        }
    }

    return measurableAndPlaceables
}


/**
 * Stacks, when when proposed infinity on their cross axis, must do an extra measurement
 * pass to determine the largest observed size that appears on that child.
 */
private fun <T: IntrinsicMeasurable> computeCrossAxisFallbackIfNeeded(
    proposedSize: Size,
    groupedMeasurables: List<List<MeasurableWithSortInfo<T>>>,
    spacingAsPx: Int
): Size {
    return Size(
            proposedSize.width,
            // fallback behaviour on the cross axis
            proposedSize.height.ifInfinity {
                val sizes = groupedMeasurables.measureOrderedHStackChildrenUsingIntrinsics(
                        Size(proposedSize.width, proposedSize.height),
                        spacingAsPx,
                )

                sizes.values.maxOfOrNull { it.height } ?: Constraints.Infinity

                // note: we can't use flex value here in lieu of doing a full intrinsics measure
                // pass, because flex minimums don't take into account the proposed cross axis. ie.,
                // vertical flex does not use accept a proposed width value.
                // That would mean that this fallback calculation here would be wrong for child
                // measurables where one dimension is a function of the other (image fit resize
                // mode, aspect ratio modifier, etc.)
            }
    )
}