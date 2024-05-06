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

import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.unit.Constraints
import io.rover.sdk.experiences.rich.compose.model.values.Axis
import io.rover.sdk.experiences.rich.compose.ui.layout.experiencesHorizontalFlex
import io.rover.sdk.experiences.rich.compose.ui.layout.experiencesVerticalFlex
import io.rover.sdk.experiences.rich.compose.ui.layout.unlessInfinity
import io.rover.sdk.experiences.rich.compose.ui.modifiers.layerModifierData


/**
 * Pre-computed details about a measurable that is being laid out in a stack.
 *
 * This serves to send some expensive information computed from the sorting/grouping step over to
 * the measurement step.
 */
internal data class MeasurableWithSortInfo<T : IntrinsicMeasurable>(
    val measurable: T,
    val flexRange: IntRange,
    val priority: Int,
) {
    /**
     * The degree of flexibility of this measurable. Used to sort layout items by their flexiblity.
     */
    val flex: Int
        get() = flexRange.last - flexRange.first
}

/**
 * Chunk the set of measurables into groups that the hstack algorithm can operate on.
 *
 * First they are grouped by their flexibility, and layout priority serves to break any ties
 * between measurables with the same flexibility.
 */
internal fun <T : IntrinsicMeasurable> Collection<T>.groupByHorizontalFlexibilityAndPriority(): List<List<MeasurableWithSortInfo<T>>> {
    val measurablesWithInfo = map { measurable ->
        MeasurableWithSortInfo(
                measurable,
                measurable.experiencesHorizontalFlex(),
                measurable.layerModifierData?.layoutPriority ?: 0,
        )
    }

    val sorted = measurablesWithInfo.sortedWith(
            Comparator { o1, o2 ->
                // Returns a negative integer, zero, or a positive integer as the first argument
                // is less than, equal to, or greater than the second.

                // Priority is the primary sort key, flex is the secondary sort key.
                return@Comparator if (o1.priority == o2.priority) {
                    // lowest flex first
                    o1.flex.compareTo(o2.flex)
                } else {
                    // we want highest priority first
                    o2.priority.compareTo(o1.priority)
                }
            },
    )

    // now, break it into groups, on Priority boundaries.
    val grouped = sorted.splitOnBoundaryChange { a, b -> a.priority != b.priority }

    return grouped
}

/**
 * Chunk the set of measurables into groups that the vstack algorithm can operate on.
 *
 * First they are grouped by their flexibility, and layout priority serves to break any ties
 * between measurables with the same flexibility.
 */
internal fun <T : IntrinsicMeasurable> Collection<T>.groupByVerticalFlexibilityAndPriority(): List<List<MeasurableWithSortInfo<T>>> {
    val measurablesWithInfo = map { measurable ->
        MeasurableWithSortInfo(
                measurable,
                measurable.experiencesVerticalFlex(),
                measurable.layerModifierData?.layoutPriority ?: 0,
        )
    }

    val sorted = measurablesWithInfo.sortedWith(
            Comparator { o1, o2 ->
                // Returns a negative integer, zero, or a positive integer as the first argument
                // is less than, equal to, or greater than the second.

                // Priority is the primary sort key, flex is the secondary sort key.
                return@Comparator if (o1.priority == o2.priority) {
                    // lowest flex first
                    o1.flex.compareTo(o2.flex)
                } else {
                    // we want highest priority first
                    o2.priority.compareTo(o1.priority)
                }
            },
    )

    // now, break it into groups, on Priority boundaries.
    val grouped = sorted.splitOnBoundaryChange { a, b -> a.priority != b.priority }

    return grouped
}

/**
 * Calculate the range of sizes that a stack can occupy along its primary axis.
 *
 * So, the horizontal flex of an hstack, and the vertical flex of a vstack.
 *
 * @param axis The axis of the stack.
 */
internal fun primaryAxisFlex(
    axis: Axis,
    measurables: List<IntrinsicMeasurable>,
    spacingAsPx: Int
): IntRange {
    val childRanges = measurables.map {
        when(axis) {
            Axis.VERTICAL -> it.experiencesVerticalFlex()
            Axis.HORIZONTAL -> it.experiencesHorizontalFlex()
        }
    }

    // we'll add spacing since spacing is inflexible.
    val spacing = maxOf(
            (spacingAsPx * (measurables.count() - 1)),
            0,
    )

    val lower = childRanges.sumOf { it.first } + spacing
    val higher = childRanges.maxOfOrNull { it.last }?.let { max -> max.unlessInfinity { it + spacing } } ?: 0

    return IntRange(lower, higher)
}

/**
 * Calculate the range of sizes that a stack can occupy along its cross axis.
 *
 * So, the vertical flex of an hstack, and the horizontal flex of a vstack.
 *
 * @param axis The axis of the stack.
 */
internal fun crossAxisFlex(
    axis: Axis,
    measurables: List<IntrinsicMeasurable>,
): IntRange {
    val childRanges = measurables.map {
        when(axis) {
            Axis.VERTICAL -> it.experiencesHorizontalFlex()
            Axis.HORIZONTAL -> it.experiencesVerticalFlex()
        }
    }

    val lower = childRanges.maxOfOrNull { it.first } ?: 0
    val upper = childRanges.maxOfOrNull { it.last } ?: 0

    return IntRange(lower, upper)
}

/**
 * Splits a collection on boundaries defined by a boundary checker function.
 */
private fun <T> Collection<T>.splitOnBoundaryChange(boundaryChecker: (T, T) -> Boolean): List<List<T>> {
    return fold(mutableListOf(mutableListOf<T>())) { acc, value ->
        if (acc.last().isNotEmpty() && boundaryChecker(acc.last().last(), value)) {
            acc.add(mutableListOf(value))
        } else {
            acc.last().add(value)
        }
        acc
    }
}


/**
 * Basic copy of the [sumOf] implementation but adding layout spacing to the return if needed.
 * Used in all StackLayers.
 */
internal inline fun <T> Iterable<T>.sumOfWithLayoutSpacing(spacingAsPx: Int, selector: (T) -> Int): Int {
    var sum: Int = 0
    for (element in this) {
        val addend = selector(element)
        if (addend == Constraints.Infinity) {
            return Constraints.Infinity
        }
        sum += addend
    }
    return sum + maxOf(spacingAsPx * (this.count() - 1), 0)
}

internal fun Iterable<Int>.sumOfWithLayoutSpacing(spacingAsPx: Int): Int {
    var sum: Int = 0
    for (element in this) {
        if (element == Constraints.Infinity) {
            return Constraints.Infinity
        }
        sum += element
    }
    return sum + maxOf(spacingAsPx * (this.count() - 1), 0)
}
