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
import io.rover.sdk.experiences.rich.compose.ui.layout.component1
import io.rover.sdk.experiences.rich.compose.ui.layout.component2
import io.rover.sdk.experiences.rich.compose.ui.layout.experiencesHorizontalFlex
import io.rover.sdk.experiences.rich.compose.ui.layout.experiencesVerticalFlex
import io.rover.sdk.experiences.rich.compose.ui.layout.fallbackMeasure
import io.rover.sdk.experiences.rich.compose.ui.layout.mapMaxIntrinsicWidthAsMeasure
import io.rover.sdk.experiences.rich.compose.ui.layout.mapMinIntrinsicAsFlex
import io.rover.sdk.experiences.rich.compose.ui.modifiers.LayerModifiers
import io.rover.sdk.experiences.rich.compose.ui.utils.LayoutInfo
import io.rover.sdk.experiences.rich.compose.ui.utils.groupByHorizontalFlexibilityAndPriority
import io.rover.sdk.experiences.rich.compose.ui.utils.ifInfinity
import io.rover.sdk.experiences.rich.compose.ui.utils.unlessInfinity

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

            val proposedSizeWithFallback = if (constraints.maxHeight == Constraints.Infinity) {
                // cross dimension fallback is needed.
                val (largestObservedNonInfinityHeight, _) = groupedMeasurables.measureOrderedHStackChildrenUsingIntrinsics(
                    Size(constraints.maxWidth, constraints.maxHeight),
                    spacingAsPx,
                )
                Size(constraints.maxWidth, largestObservedNonInfinityHeight ?: Constraints.Infinity)
            } else {
                // no fallback needed.
                Size(constraints.maxWidth, constraints.maxHeight)
            }

            val measurableAndPlaceablePairs = groupedMeasurables.measureOrderedHStackChildren(
                proposedSizeWithFallback,
                spacingAsPx,
            )

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
                return mapMaxIntrinsicWidthAsMeasure(height) { (proposedWidth, proposedHeight) ->
                    if (measurables.isEmpty()) {
                        return@mapMaxIntrinsicWidthAsMeasure Size(0, 0)
                    }

                    val groupedMeasurables = measurables.groupByHorizontalFlexibilityAndPriority()

                    // fallback behaviour on the cross dimension
                    val maxHeightConstraint = proposedHeight.ifInfinity {
                        val (largestObservedNonInfinityHeight, childSizes) = groupedMeasurables.measureOrderedHStackChildrenUsingIntrinsics(
                            Size(proposedWidth, proposedHeight),
                            spacingAsPx,
                        )

                        largestObservedNonInfinityHeight ?: Constraints.Infinity
                    }

                    // now I have a fallback cross dim value (if needed, otherwise stick with the originally proposed value).
                    //
                    // Now run the stack algo again.

                    // TODO: optimization: this can be only done in the event of needing a fallback!
                    val measureResults = groupedMeasurables.measureOrderedHStackChildrenUsingIntrinsics(
                        Size(
                            proposedWidth,
                            maxHeightConstraint,
                        ),
                        spacingAsPx,
                    )

                    val finalChildSizes = measureResults.childSizes.values

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
                    val childRanges = measurables.map { it.experiencesHorizontalFlex() }

                    // we'll add spacing since spacing is inflexible.
                    val spacing = maxOf(
                        (spacingAsPx * (measurables.count() - 1)),
                        0,
                    )

                    val lower = childRanges.sumOf { it.first } + spacing
                    val higher = childRanges.maxOfOrNull { it.last }?.let { max -> max.unlessInfinity { it + spacing } } ?: 0
                    IntRange(lower, higher)
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
                    val childRanges = measurables.map { it.experiencesVerticalFlex() }

                    val lower = childRanges.maxOfOrNull { it.first } ?: 0
                    val upper = childRanges.maxOfOrNull { it.last } ?: 0

                    IntRange(lower, upper)
                }
            } finally {
                Trace.endSection()
            }
        }

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int {
            throw IllegalStateException("Only call maxIntrinsicWidth, with packed parameter, on Rover Experiences measurables.")
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

private data class HStackChildIntrinsicMeasureResults(
    val largestObservedNonInfinityHeight: Int?,
    val childSizes: Map<IntrinsicMeasurable, Size>,
)

private fun <T : IntrinsicMeasurable> Collection<Collection<LayoutInfo<T>>>.measureOrderedHStackChildrenUsingIntrinsics(
    proposedSize: Size,
    spacingAsPx: Int,
): HStackChildIntrinsicMeasureResults {
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

    var largestObservedNonInfinityHeight: Int? = null

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
                    if (priorityGroupIndex == 0) {
                        // in the highest priority group, so track the largest observed height
                        // for infinity behaviour.
                        if (childSize.height != Constraints.Infinity) {
                            largestObservedNonInfinityHeight = maxOf(
                                largestObservedNonInfinityHeight ?: Int.MIN_VALUE,
                                childSize.height,
                            )
                        }
                    }
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

                    if (priorityGroupIndex == 0) {
                        // in the highest priority group, so track the largest observed height
                        // for infinity behaviour.
                        if (childSize.height != Constraints.Infinity) {
                            largestObservedNonInfinityHeight = maxOf(
                                largestObservedNonInfinityHeight ?: Int.MIN_VALUE,
                                childSize.height,
                            )
                        }
                    }

                    remainingWidth -= childSize.width

                    childSizes[layoutInfo.measurable] = childSize
                }
            }
        }
    }

    return HStackChildIntrinsicMeasureResults(
        largestObservedNonInfinityHeight,
        childSizes,
    )
}

private fun Collection<Collection<LayoutInfo<Measurable>>>.measureOrderedHStackChildren(
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
                    val remainingMinimum = remainingFlexMinimumChildren.map { it.flexRange.first }.filter { it != Constraints.Infinity}.sum()
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
