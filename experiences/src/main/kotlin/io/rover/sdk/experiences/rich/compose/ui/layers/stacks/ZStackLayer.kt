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

import android.graphics.Point
import android.os.Trace
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.rover.sdk.experiences.rich.compose.model.nodes.ZStack
import io.rover.sdk.experiences.rich.compose.model.values.Alignment
import io.rover.sdk.experiences.rich.compose.ui.layers.ApplyLayerModifiers
import io.rover.sdk.experiences.rich.compose.ui.layers.Children
import io.rover.sdk.experiences.rich.compose.ui.layout.*
import io.rover.sdk.experiences.rich.compose.ui.modifiers.LayerModifiers
import io.rover.sdk.experiences.rich.compose.ui.modifiers.layerModifierData
import io.rover.sdk.experiences.rich.compose.ui.modifiers.setLayerModifierData
import io.rover.sdk.experiences.rich.compose.ui.utils.ifInfinity
import io.rover.sdk.experiences.rich.compose.ui.utils.preview.FixedSizeModifier

@Composable
internal fun ZStackLayer(node: ZStack, modifier: Modifier = Modifier) {
    ZStackLayer(node.alignment, layerModifiers = LayerModifiers(node), modifier = modifier) {
        Children(children = node.children, modifier = Modifier)
    }
}

@Composable
internal fun ZStackLayer(
    alignment: Alignment = Alignment.CENTER,
    layerModifiers: LayerModifiers = LayerModifiers(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ApplyLayerModifiers(layerModifiers, modifier = modifier) { modifier ->
        Layout(content, measurePolicy = zStackMeasurePolicy(alignment), modifier = modifier)
    }
}

internal fun zStackMeasurePolicy(alignment: Alignment): MeasurePolicy {
    return object : MeasurePolicy {

        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult {
            if (measurables.isEmpty()) {
                return layout(0, 0) { }
            }

            Trace.beginSection("ZStack::measure")

            data class ZStackChild(
                val measurable: Measurable,
                val zIndex: Int,
                val priority: Int,
            )

            val children = measurables.mapIndexed { index, measurable ->
                ZStackChild(
                    measurable = measurable,
                    zIndex = index,
                    priority = measurable.layerModifierData?.layoutPriority ?: 0,
                )
            }

            val maxPriority = children.maxOf { it.priority }

            val maxPriorityChildren = children
                .filter { child -> child.priority == maxPriority }

            val lowPriorityMeasurables = children
                .filter { child -> child.priority != maxPriority }

            data class PlaceableChild(
                val placeable: Placeable,
                val zIndex: Int,
            )

            Trace.beginSection("ZStack::measure::fallback")
            // ZStack needs to know what to propose to all the high-priority children
            // in the event of the ZStack itself being proposed an Infinity.
            // Additionally to allow high priority children to force the ZStack
            // to be a larger size (and thus proposing that larger size to all the other
            // children.)
            val childSizes = maxPriorityChildren.map {
                it.measurable.fallbackMeasure(
                    Size(
                        constraints.maxWidth,
                        constraints.maxHeight,
                    ),
                )
            }

            // identify the largest (non-infinity, infinity size returned by child means fallback
            // couldn't be calculated for it) child size for each dimension, and we'll
            // use that to propose to children in lieu of infinity.
            val fallbackWidth = childSizes.map { it.width }.filter { it != Constraints.Infinity }.maxOrNull() ?: constraints.maxWidth
            val fallbackHeight = childSizes.map { it.height }.filter { it != Constraints.Infinity }.maxOrNull() ?: constraints.maxHeight

            val measureSize = Size(
                maxOf(constraints.maxWidth.ifInfinity { fallbackWidth }, fallbackWidth),
                maxOf(constraints.maxHeight.ifInfinity { fallbackHeight }, fallbackHeight),
            )

            Trace.endSection()

            val maxPriorityPlaceables = maxPriorityChildren.map { child ->
                val childConstraints = constraints.copy(
                    minWidth = 0,
                    maxWidth = measureSize.width,
                    minHeight = 0,
                    maxHeight = measureSize.height,
                )
                PlaceableChild(
                    child.measurable.measure(
                        childConstraints,
                    ),
                    child.zIndex,
                )
            }

            val width = maxPriorityPlaceables.maxOf { it.placeable.measuredWidth }
            val height = maxPriorityPlaceables.maxOf { it.placeable.measuredHeight }

            val lowPriorityPlaceables = lowPriorityMeasurables.map { child ->
                val childConstraints = constraints.copy(
                    minWidth = 0,
                    maxWidth = width,
                    minHeight = 0,
                    maxHeight = height,
                )
                PlaceableChild(
                    child.measurable.measure(
                        childConstraints,
                    ),
                    child.zIndex,
                )
            }

            val l = layout(width, height) {
                val placeableChildren = maxPriorityPlaceables + lowPriorityPlaceables
                placeableChildren.forEach { child ->
                    val placeable = child.placeable
                    val centerWidth = { width / 2 - placeable.measuredWidth / 2 }
                    val centerHeight = { height / 2 - placeable.measuredHeight / 2 }
                    val right = { width - placeable.measuredWidth }
                    val bottom = { height - placeable.measuredHeight }

                    val position: Point = when (alignment) {
                        Alignment.TOP -> Point(centerWidth(), 0)
                        Alignment.BOTTOM -> Point(centerWidth(), bottom())
                        Alignment.LEADING -> Point(0, centerHeight())
                        Alignment.TRAILING -> Point(right(), centerHeight())
                        Alignment.TOP_LEADING -> Point(0, 0)
                        Alignment.TOP_TRAILING -> Point(right(), 0)
                        Alignment.BOTTOM_LEADING -> Point(0, bottom())
                        Alignment.BOTTOM_TRAILING -> Point(right(), bottom())
                        Alignment.FIRST_TEXT_BASELINE -> Point(
                            0,
                            0,
                        ) // TODO: This alignment type has its own issue: https://github.com/judoapp/judo-android-develop/issues/636
                        else -> Point(
                            centerWidth(),
                            centerHeight(),
                        )
                    }
                    placeable.place(
                        (placeable.measuredWidth - placeable.width) / 2 + position.x,
                        (placeable.measuredHeight - placeable.height) / 2 + position.y,
                        zIndex = child.zIndex * -1f,
                    )
                }
            }
            Trace.endSection()
            return l
        }

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int {
            Trace.beginSection("ZStack::intrinsicMeasure")
            return try {
                this.mapMaxIntrinsicWidthAsMeasure(height) { (proposedWidth, proposedHeight) ->
                    if (measurables.isEmpty()) {
                        return@mapMaxIntrinsicWidthAsMeasure Size(0, 0)
                    }

                    data class ZStackChild(
                        val measurable: IntrinsicMeasurable,
                        val zIndex: Int,
                        val priority: Int,
                    )

                    val children = measurables.mapIndexed { index, measurable ->
                        ZStackChild(
                            measurable = measurable,
                            zIndex = index,
                            priority = measurable.layerModifierData?.layoutPriority ?: 0,
                        )
                    }

                    val maxPriority = children.maxOf { it.priority }

                    val maxPriorityChildren = children
                        .filter { child -> child.priority == maxPriority }

                    val lowPriorityMeasurables = children
                        .filter { child -> child.priority != maxPriority }

                    data class PlaceableChild(
                        val size: Size,
                        val zIndex: Int,
                    )

                    Trace.beginSection("ZStack::intrinsicMeasure::fallback")
                    // ZStack needs to know what to propose to all the high-priority children
                    // in the event of the ZStack itself being proposed an Infinity.
                    // Additionally to allow high priority children to force the ZStack
                    // to be a larger size (and thus proposing that larger size to all the other
                    // children.)
                    val childSizes = maxPriorityChildren.map {
                        it.measurable.fallbackMeasure(
                            Size(
                                proposedWidth,
                                proposedHeight,
                            ),
                        )
                    }

                    val fallbackWidth = childSizes.map { it.width }.filter { it != Constraints.Infinity }.maxOrNull() ?: proposedWidth
                    val fallbackHeight = childSizes.map { it.height }.filter { it != Constraints.Infinity }.maxOrNull() ?: proposedHeight

                    val measureSize = Size(
                        maxOf(proposedWidth.ifInfinity { fallbackWidth }, proposedWidth),
                        maxOf(proposedHeight.ifInfinity { fallbackHeight }, proposedHeight),
                    )

                    Trace.endSection()

                    val maxPriorityPlaceables = maxPriorityChildren.map { child ->
                        PlaceableChild(
                            child.measurable.fallbackMeasure(
                                Size(
                                    measureSize.width,
                                    measureSize.height,
                                ),
                            ),
                            child.zIndex,
                        )
                    }

                    Size(
                        maxPriorityPlaceables.maxOf { it.size.width },
                        maxPriorityPlaceables.maxOf { it.size.height },
                    )
                }
            } finally {
                Trace.endSection()
            }
        }

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int {
            return try {
                Trace.beginSection("ZStackLayer::intrinsicMeasure::verticalFlex")
                mapMinIntrinsicAsFlex {
                    // zstack flex would be constrained, for both dimensions, the most
                    // inflexible child. oh snap layout priority changes the behavior, only top?
                    // (For hstack/hvstack, I believe layout priority not material to flex, but
                    // here, because of Zstack's fallback behaviour, it is.)

                    data class ZStackChild(
                        val measurable: IntrinsicMeasurable,
                        val zIndex: Int,
                        val priority: Int,
                    )

                    // TODO: all this may produce a lot of garbage for the hotpath. Optimize,
                    //  potentially.
                    val children = measurables.mapIndexed { index, measurable ->
                        ZStackChild(
                            measurable = measurable,
                            zIndex = index,
                            priority = measurable.layerModifierData?.layoutPriority ?: 0,
                        )
                    }

                    val maxPriority = children.maxOfOrNull { it.priority } ?: 0

                    val maxPriorityChildren = children
                        .filter { child -> child.priority == maxPriority }

                    val childRanges = maxPriorityChildren.map { it.measurable.experiencesVerticalFlex() }

                    val lower = childRanges.maxOfOrNull { it.first } ?: 0
                    val upper = childRanges.maxOfOrNull { it.last } ?: 0

                    IntRange(lower, upper)
                }
            } finally {
                Trace.endSection()
            }
        }

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int {
            return try {
                Trace.beginSection("ZStackLayer::intrinsicMeasure::horizontalFlex")
                mapMinIntrinsicAsFlex {
                    // zstack flex would be constrained, for both dimensions, the most
                    // inflexible child. oh snap layout priority changes the behavior, only top?
                    // (For hstack/hvstack, I believe layout priority not material to flex, but
                    // here, because of Zstack's fallback behaviour, it is.)

                    data class ZStackChild(
                        val measurable: IntrinsicMeasurable,
                        val zIndex: Int,
                        val priority: Int,
                    )

                    // TODO: all this may produce a lot of garbage for the hotpath. Optimize,
                    //  potentially.
                    val children = measurables.mapIndexed { index, measurable ->
                        ZStackChild(
                            measurable = measurable,
                            zIndex = index,
                            priority = measurable.layerModifierData?.layoutPriority ?: 0,
                        )
                    }

                    val maxPriority = children.maxOfOrNull { it.priority } ?: 0

                    val maxPriorityChildren = children
                        .filter { child -> child.priority == maxPriority }

                    val childRanges = maxPriorityChildren.map { it.measurable.experiencesHorizontalFlex() }

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

@Composable
private fun TestBox(
    modifier: Modifier = Modifier,
    size: Dp = 25.dp,
) {
    Box(
        modifier = modifier.then(FixedSizeModifier(size, size))
            .background(Color.Red)
            .requiredSize(size),
    )
}

/**
 * This is a larger box, meant to demonstrate an oversized child.
 */
@Composable
private fun BackgroundBox(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
) {
    Box(
        modifier = modifier.then(FixedSizeModifier(size, size))
            .background(Color.Blue)
            .requiredSize(size),
    )
}

@Preview
@Composable
private fun CenterAlign() {
    ZStackLayer(alignment = Alignment.CENTER) {
        TestBox()
        BackgroundBox()
    }
}

@Preview
@Composable
private fun TopAlign() {
    ZStackLayer(alignment = Alignment.TOP) {
        TestBox()
        BackgroundBox()
    }
}

@Preview
@Composable
private fun BottomAlign() {
    ZStackLayer(alignment = Alignment.BOTTOM) {
        TestBox()
        BackgroundBox()
    }
}

@Preview
@Composable
private fun LeadingAlign() {
    ZStackLayer(alignment = Alignment.LEADING) {
        TestBox()
        BackgroundBox()
    }
}

@Preview
@Composable
private fun TrailingAlign() {
    ZStackLayer(alignment = Alignment.TRAILING) {
        TestBox()
        BackgroundBox()
    }
}

@Preview
@Composable
private fun TopTrailingAlign() {
    ZStackLayer(alignment = Alignment.TOP_TRAILING) {
        TestBox()
        BackgroundBox()
    }
}

@Preview
@Composable
private fun BottomTrailingAlign() {
    ZStackLayer(alignment = Alignment.BOTTOM_TRAILING) {
        TestBox()
        BackgroundBox()
    }
}

@Preview
@Composable
private fun TopLeadingAlign() {
    ZStackLayer(alignment = Alignment.TOP_LEADING) {
        TestBox()
        BackgroundBox()
    }
}

@Preview
@Composable
private fun BottomLeadingAlign() {
    ZStackLayer(alignment = Alignment.BOTTOM_LEADING) {
        TestBox()
        BackgroundBox()
    }
}

// And, when the 'background' child is larger:

@Preview
@Composable
private fun OverdrawCenterAlign() {
    // to show the content overdrawn from the zstack, we have to make the preview area larger.
    // hence the Modifier.size().
    ZStackLayer(alignment = Alignment.CENTER, modifier = Modifier.size(200.dp)) {
        TestBox()
        // layout priority -1 so the Zstack won't take this child into account when sizing, causing
        // it to overdraw.
        BackgroundBox(modifier = Modifier.setLayerModifierData(-1, null))
    }
}

@Preview
@Composable
private fun OverdrawTrailingAlign() {
    // to show the content overdrawn from the zstack, we have to make the preview area larger.
    // hence the Modifier.size().
    ZStackLayer(alignment = Alignment.TRAILING, modifier = Modifier.size(200.dp)) {
        TestBox()
        // layout priority -1 so the Zstack won't take this child into account when sizing, causing
        // it to overdraw.
        BackgroundBox(modifier = Modifier.setLayerModifierData(-1, null))
    }
}
