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

package io.rover.sdk.experiences.rich.compose.ui.utils

import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.unit.Constraints
import io.rover.sdk.experiences.rich.compose.ui.layout.experiencesHorizontalFlex
import io.rover.sdk.experiences.rich.compose.ui.layout.experiencesVerticalFlex
import io.rover.sdk.experiences.rich.compose.ui.modifiers.layerModifierData

/**
 * Perform some operation on an Int value unless it is a [Constraints.Infinity] value, in which case
 * pass the infinity through.
 *
 * This is often useful when processing handling maxWidth/maxHeight values arriving via
 * [Constraints] in measurement policies.
 */
internal inline fun Int.unlessInfinity(map: (Int) -> Int): Int =
    if (this == Constraints.Infinity) this else map(this)

/**
 * If an Int value is an infinity, replace it with the value yielded by [fallback].
 */
internal inline fun Int.ifInfinity(fallback: (Int) -> Int): Int =
    if (this == Constraints.Infinity) fallback(this) else this

internal inline fun Int.ifZero(fallback: () -> Int): Int =
    if (this == 0) fallback() else this

/**
 * Perform some operation on a Float value unless it is a [Float.isInfinite] value, in which case
 * pass the infinity through.
 */
internal inline fun Float.unlessInfinity(map: (Float) -> Float): Float =
    if (this.isInfinite()) this else map(this)

/**
 * If a Float value is an infinity, replace it with the value yielded by [fallback].
 */
internal inline fun Float.ifInfinity(fallback: (Float) -> Float): Float =
    if (this.isInfinite()) fallback(this) else this

/**
 * Pre-computed details about a measurable that is being laid out.
 */
internal data class LayoutInfo<T : IntrinsicMeasurable>(
    val measurable: T,
    val flexRange: IntRange,
    val priority: Int,
) {
    val flex: Int
        get() = flexRange.last - flexRange.first
}

// The following are several routines for grouping and processing measurables.

/**
 * Chunk the set of measurables into groups that the hstack algorithm can operate on.
 *
 * First they are grouped by their flexibility, and layout priority serves to break any ties
 * between measurables with the same flexibility.
 */
internal fun <T : IntrinsicMeasurable> Collection<T>.groupByHorizontalFlexibilityAndPriority(): List<List<LayoutInfo<T>>> {
    val measurablesWithInfo = map { measurable ->
        LayoutInfo(
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
internal fun <T : IntrinsicMeasurable> Collection<T>.groupByVerticalFlexibilityAndPriority(): List<List<LayoutInfo<T>>> {
    val measurablesWithInfo = map { measurable ->
        LayoutInfo(
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
