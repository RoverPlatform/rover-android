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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.rover.sdk.experiences.rich.compose.model.nodes.VStack
import io.rover.sdk.experiences.rich.compose.model.values.Alignment
import io.rover.sdk.experiences.rich.compose.model.values.Axis
import io.rover.sdk.experiences.rich.compose.model.values.ColorReference
import io.rover.sdk.experiences.rich.compose.model.values.Fill
import io.rover.sdk.experiences.rich.compose.model.values.Frame
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.layers.ApplyLayerModifiers
import io.rover.sdk.experiences.rich.compose.ui.layers.Children
import io.rover.sdk.experiences.rich.compose.ui.layers.RectangleLayer
import io.rover.sdk.experiences.rich.compose.ui.layers.SpacerLayer
import io.rover.sdk.experiences.rich.compose.ui.layers.TextLayer
import io.rover.sdk.experiences.rich.compose.ui.layout.annotateIntrinsicsCrash
import io.rover.sdk.experiences.rich.compose.ui.layout.component1
import io.rover.sdk.experiences.rich.compose.ui.layout.component2
import io.rover.sdk.experiences.rich.compose.ui.layout.experiencesHorizontalFlex
import io.rover.sdk.experiences.rich.compose.ui.layout.experiencesVerticalFlex
import io.rover.sdk.experiences.rich.compose.ui.layout.fallbackMeasure
import io.rover.sdk.experiences.rich.compose.ui.layout.mapMaxIntrinsicWidthAsMeasure
import io.rover.sdk.experiences.rich.compose.ui.layout.mapMinIntrinsicAsFlex
import io.rover.sdk.experiences.rich.compose.ui.modifiers.LayerModifiers
import io.rover.sdk.experiences.rich.compose.ui.modifiers.experiencesFrame
import io.rover.sdk.experiences.rich.compose.ui.utils.LayoutInfo
import io.rover.sdk.experiences.rich.compose.ui.utils.groupByVerticalFlexibilityAndPriority
import io.rover.sdk.experiences.rich.compose.ui.utils.ifInfinity
import io.rover.sdk.experiences.rich.compose.ui.utils.preview.InfiniteHeightMeasurePolicy
import io.rover.sdk.experiences.rich.compose.ui.utils.unlessInfinity

@Composable
internal fun VStackLayer(node: VStack, modifier: Modifier = Modifier) {
    VStackLayer(modifier, node.spacing, node.alignment, LayerModifiers(node)) {
        Children(children = node.children, modifier = Modifier)
    }
}

@Composable
internal fun VStackLayer(
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

    ApplyLayerModifiers(layerModifiers = layerModifiers, modifier = modifier) { modifier ->
        CompositionLocalProvider(Environment.LocalStackAxis provides Axis.VERTICAL) {
            Layout(content, measurePolicy = vStackMeasurePolicy(spacingAsPx, alignment), modifier = modifier)
        }
    }
}

/**
 * Measure policy for the VStackLayer.
 * It overrides the intrinsic height AND width functions to remove the default behavior and better handle
 * infinity in children widths.
 *
 * For the height intrinsics, we always need to account for the spacing between children.
 * This is especially true in the layout function as otherwise the children will be bigger than the
 * VStack layout and go over its borders.
 *
 * The flexibility sorting is quite simple, ranking children by the range of heights they can be while
 * still being correctly drawn. The bigger this range, the more flexible a child is. For rectangles
 * without frames, for example, the range is between 0 and [Constraints.Infinity], always being one
 * of the most flexible layers.
 */
internal fun vStackMeasurePolicy(spacingAsPx: Int, alignment: Alignment): MeasurePolicy {
    val tag = "VStackMeasurePolicy"
    return object : MeasurePolicy {

        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult {
            if (measurables.isEmpty()) {
                return layout(0, 0) { }
            }

            Trace.beginSection("VStackLayer::measure")

            val groupedMeasurables = measurables.groupByVerticalFlexibilityAndPriority()

            val proposedSizeWithFallback = if (constraints.maxWidth == Constraints.Infinity) {
                // cross dimension fallback is needed.
                val (largestObservedNonInfinityWidth, _) = groupedMeasurables.measureOrderedVStackChildrenUsingIntrinsics(
                    Size(constraints.maxWidth, constraints.maxHeight),
                    spacingAsPx,
                )
                Size(
                    largestObservedNonInfinityWidth ?: Constraints.Infinity,
                    constraints.maxHeight,
                )
            } else {
                // no fallback needed
                Size(constraints.maxWidth, constraints.maxHeight)
            }

            val measurableAndPlaceablePairs = groupedMeasurables.measureOrderedVStackChildren(
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

            val maxWidth = placeables.maxOf { it.measuredWidth }
            val height = placeables.sumOfWithLayoutSpacing(spacingAsPx) { it.measuredHeight }

            val l = layout(maxWidth, height) {
                var yPosition = 0

                placeables.forEach { placeable ->
                    val xPosition = when (alignment) {
                        Alignment.LEADING -> 0
                        Alignment.TRAILING -> maxWidth - placeable.measuredWidth
                        // The default Alignment for VStack is CENTER.
                        else -> maxOf(maxWidth / 2 - placeable.measuredWidth / 2, 0)
                    }

                    placeable.placeRelative(
                        x = (placeable.measuredWidth - placeable.width) / 2 + xPosition,
                        y = (placeable.measuredHeight - placeable.height) / 2 + yPosition,
                    )
                    yPosition += placeable.measuredHeight + spacingAsPx
                }
            }
            Trace.endSection()
            return l
        }

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int {
            Trace.beginSection("VStackLayer::intrinsicMeasure")

            try {
                return mapMaxIntrinsicWidthAsMeasure(height) { (proposedWidth, proposedHeight) ->
                    if (measurables.isEmpty()) {
                        return@mapMaxIntrinsicWidthAsMeasure Size(0, 0)
                    }

                    val groupedMeasurables = measurables.groupByVerticalFlexibilityAndPriority()

                    // fallback behaviour on the cross dimension
                    val maxWidthConstraint = proposedWidth.ifInfinity {
                        val (largestObservedNonInfinityWidth, childSizes) = groupedMeasurables.measureOrderedVStackChildrenUsingIntrinsics(
                            Size(proposedWidth, proposedHeight),
                            spacingAsPx,
                        )

                        largestObservedNonInfinityWidth ?: Constraints.Infinity
                    }

                    // now I have a fallback cross dim value (if needed, otherwise stick with the originally proposed value).
                    //
                    // Now run the stack algo again.

                    // TODO: optimization: this can be only done in the event of needing a fallback!
                    val measureResults = groupedMeasurables.measureOrderedVStackChildrenUsingIntrinsics(
                        Size(
                            maxWidthConstraint,
                            proposedHeight,
                        ),
                        spacingAsPx,
                    )

                    val finalChildSizes = measureResults.childSizes.values

                    val maxWidth = finalChildSizes.maxOf { it.width }
                    val height = finalChildSizes.sumOfWithLayoutSpacing(spacingAsPx) { it.height }

                    Size(maxWidth, height)
                }
            } finally {
                Trace.endSection()
            }
        }

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int {
            Trace.beginSection("VStackLayer::intrinsicMeasure::horizontalFlex")
            return try {
                mapMinIntrinsicAsFlex {
                    val childRanges = measurables.map { it.experiencesHorizontalFlex() }

                    val lower = childRanges.maxOfOrNull { it.first } ?: 0
                    val upper = childRanges.maxOfOrNull { it.last } ?: 0

                    IntRange(lower, upper)
                }
            } finally {
                Trace.endSection()
            }
        }

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int {
            Trace.beginSection("VStackLayer::intrinsicMeasure::verticalFlex")
            return try {
                mapMinIntrinsicAsFlex {
                    val childRanges = measurables.map { it.experiencesVerticalFlex() }

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

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int {
            throw IllegalStateException("Only call maxIntrinsicWidth, with packed parameter, on Rover Experiences measurables.")
        }
    }
}

private data class VStackChildIntrinsicMeasureResults(
    val largestObservedNonInfinityWidth: Int?,
    val childSizes: Map<IntrinsicMeasurable, Size>,
)

private fun <T: IntrinsicMeasurable> Collection<Collection<LayoutInfo<T>>>.measureOrderedVStackChildrenUsingIntrinsics(
    proposedSize: Size,
    spacingAsPx: Int,
): VStackChildIntrinsicMeasureResults {
    val totalChildren = this.sumOf { it.count() }
    val totalSpacing = maxOf(
        (spacingAsPx * (totalChildren - 1)),
        0,
    )
    var remainingHeight =
        if (proposedSize.height == Constraints.Infinity) {
            Constraints.Infinity
        } else {
            proposedSize.height - totalSpacing
        }

    val childSizes = hashMapOf<IntrinsicMeasurable, Size>()

    var largestObservedNonInfinityWidth: Int? = null

    // this is a mutable set of all remaining children to be measured, in order to obtain
    // their flex minimums.
    val remainingFlexMinimumChildren = this.flatten().toMutableSet()

    forEachIndexed { priorityGroupIndex, measurables ->
        measurables.forEachIndexed { measurableIndex, layoutInfo ->
            layoutInfo.measurable.annotateIntrinsicsCrash {
                if (remainingHeight == Constraints.Infinity) {
                    val childSize = layoutInfo.measurable.fallbackMeasure(
                        Size(
                            proposedSize.width,
                            Constraints.Infinity,
                        ),
                    )
                    childSizes[layoutInfo.measurable] = childSize
                    if (priorityGroupIndex == 0) {
                        // in the highest priority group, so track the largest observed height
                        // for infinity behaviour.
                        if (childSize.width != Constraints.Infinity) {
                            largestObservedNonInfinityWidth = maxOf(
                                largestObservedNonInfinityWidth ?: Int.MIN_VALUE,
                                childSize.width,
                            )
                        }
                    }
                    // what to do in both branches if child height is infinity? if width is infinity?

                    // for height (stack axis) -> just return infinity

                    // for width -> just return infinity.
                } else {
                    remainingFlexMinimumChildren.removeIf { it.measurable == layoutInfo.measurable }
                    val remainingMinimum = remainingFlexMinimumChildren.map { it.flexRange.first }.filter { it != Constraints.Infinity}.sum()

                    val remainingChildren = measurables.size - measurableIndex
                    val proposeToChild = maxOf((remainingHeight - remainingMinimum) / remainingChildren, 0)

                    val childSize = layoutInfo.measurable.fallbackMeasure(
                        Size(
                            proposedSize.width,
                            proposeToChild,
                        ),
                    )

                    if (priorityGroupIndex == 0) {
                        // in the highest priority group, so track the largest observed height
                        // for infinity behaviour.
                        if (childSize.width != Constraints.Infinity) {
                            largestObservedNonInfinityWidth = maxOf(
                                largestObservedNonInfinityWidth ?: Int.MIN_VALUE,
                                childSize.width,
                            )
                        }
                    }

                    remainingHeight -= childSize.height

                    childSizes[layoutInfo.measurable] = childSize
                }
            }
        }
    }

    return VStackChildIntrinsicMeasureResults(
        largestObservedNonInfinityWidth,
        childSizes,
    )
}

private fun Collection<Collection<LayoutInfo<Measurable>>>.measureOrderedVStackChildren(
    proposedSize: Size,
    spacingAsPx: Int,
): List<Pair<Measurable, Placeable>> {
    val totalChildren = this.sumOf { it.count() }
    val totalSpacing = maxOf(
        (spacingAsPx * (totalChildren - 1)),
        0,
    )
    var remainingHeight =
        if (proposedSize.height == Constraints.Infinity) {
            Constraints.Infinity
        } else {
            proposedSize.height - totalSpacing
        }

    // this is a mutable set of all remaining children to be measured, in order to obtain
    // their flex minimums.
    val remainingFlexMinimumChildren = this.flatten().toMutableSet()

    val measurableAndPlaceables = flatMap { measurables ->
        measurables.mapIndexed { index, layoutInfo ->
            layoutInfo.measurable.annotateIntrinsicsCrash {
                if (remainingHeight == Constraints.Infinity) {
                    Pair(
                        layoutInfo.measurable,
                        layoutInfo.measurable.measure(
                            Constraints(
                                maxWidth = proposedSize.width,
                                maxHeight = Constraints.Infinity,
                            ),
                        ),
                    )
                } else {
                    val remainingChildren: Int = measurables.size - index

                    // this is a mutable set of all remaining children to be measured, in order to obtain
                    // their flex minimums.
                    remainingFlexMinimumChildren.removeIf { it.measurable == layoutInfo.measurable }
                    val remainingMinimum = remainingFlexMinimumChildren
                            // filter for only the lower priority children
                            .filter { it.priority < layoutInfo.priority }
                            // take the minimum bound of flex range (excluding infinity)
                            .map { it.flexRange.first }.filter { it != Constraints.Infinity}.sum()

                    val proposeToChild = maxOf((remainingHeight - remainingMinimum) / remainingChildren, 0)

                    val placeable = layoutInfo.measurable.measure(
                        Constraints(
                            maxWidth = proposedSize.width,
                            maxHeight = proposeToChild,
                        ),
                    )

                    remainingHeight -= placeable.measuredHeight

                    Pair(layoutInfo.measurable, placeable)
                }
            }
        }
    }

    return measurableAndPlaceables
}

@Composable
private fun TestBox(
    modifier: Modifier = Modifier,
    size: Dp = 25.dp,
) {
    RectangleLayer(
        fill = Fill.FlatFill(ColorReference.SystemColor("red")),
        cornerRadius = 8f,
        modifier = modifier.experiencesFrame(
            Frame(width = size.value, height = size.value, alignment = Alignment.CENTER),
        ),
    )
}

@Preview
@Composable
private fun StackCenterAligned() {
    VStackLayer(alignment = Alignment.CENTER) {
        TestBox()
        TextLayer(text = "Rover rules")
    }
}

@Preview
@Composable
private fun StackInfiniteContent() {
    VStackLayer(alignment = Alignment.CENTER) {
        RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("blue")), cornerRadius = 8f)
        TextLayer(text = "Rover rules")
        RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("red")), cornerRadius = 8f)
    }
}

@Preview
@Composable
private fun OfferedInfinityHeight() {
    // the scroll container will offer the stack infinity height.  We want to test this use case
    // explicitly without depending on ScrollContainerLayer (which itself has an implicit VStack in
    // it, making for an awkward test situation)

    Layout({
        VStackLayer() {
            // these rectangles should be offered infinity on height, and ExpandMeasurePolicy
            // should fall back to 10 dp.
            RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("blue")))
            RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("red")))
        }
    }, measurePolicy = InfiniteHeightMeasurePolicy)
}

@Preview
@Composable
private fun IntegrationSpacer() {
    VStackLayer() {
        TextLayer("Rover")
        SpacerLayer()
        TextLayer("Rocks")
    }
}

@Preview
@Composable
private fun LongContent() {
    VStackLayer {
        RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("red")), modifier = Modifier.experiencesFrame(Frame(height = 100f, alignment = Alignment.CENTER)))
        RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("green")), modifier = Modifier.experiencesFrame(Frame(height = 100f, alignment = Alignment.CENTER)))
        RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("blue")), modifier = Modifier.experiencesFrame(Frame(height = 100f, alignment = Alignment.CENTER)))

        RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("red")), modifier = Modifier.experiencesFrame(Frame(height = 100f, alignment = Alignment.CENTER)))
        RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("green")), modifier = Modifier.experiencesFrame(Frame(height = 100f, alignment = Alignment.CENTER)))
        RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("blue")), modifier = Modifier.experiencesFrame(Frame(height = 100f, alignment = Alignment.CENTER)))

        RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("red")), modifier = Modifier.experiencesFrame(Frame(height = 100f, alignment = Alignment.CENTER)))
        RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("green")), modifier = Modifier.experiencesFrame(Frame(height = 100f, alignment = Alignment.CENTER)))
        RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("blue")), modifier = Modifier.experiencesFrame(Frame(height = 100f, alignment = Alignment.CENTER)))
    }
}
