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

package io.rover.sdk.experiences.rich.compose.ui.layout

import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.unit.Constraints
import io.rover.sdk.experiences.rich.compose.ui.modifiers.LayerModifierData
import io.rover.sdk.experiences.rich.compose.ui.utils.groupByHorizontalFlexibilityAndPriority
import junit.framework.TestCase.assertEquals
import org.junit.Test

class StackHelpersTest {
    @Test
    fun `groups same flexibility measurables by priority`() {
        // these all have same flex.
        val measurables = listOf(
            SortTestMeasurable(priority = 1),
            SortTestMeasurable(priority = 2),
            SortTestMeasurable(priority = 1),
        )

        val grouped = measurables.groupByHorizontalFlexibilityAndPriority()

        assertEquals(2, grouped.size)

        assertEquals(1, grouped[0].size)
        assertEquals(2, grouped[1].size)
    }

    @Test
    fun `orders by flexibility`() {
        val measurables = listOf(
            SortTestMeasurable(name = "infinitely flexible", horizFlex = IntRange(0, Constraints.Infinity)),
            SortTestMeasurable(name = "inflexible", horizFlex = IntRange(50, 50)),
            SortTestMeasurable(name = "slightly inflexible", horizFlex = IntRange(100, 150)),
        )

        val grouped = measurables.groupByHorizontalFlexibilityAndPriority()

        // only 1 priority level (the default), so only one group.
        assertEquals(1, grouped.size)

        val ordered = grouped.first()

        assertEquals(3, ordered.size)

        val result = ordered.map { it.measurable.name }

        assertEquals(listOf("inflexible", "slightly inflexible", "infinitely flexible"), result)
    }
}

private class SortTestMeasurable(
    val name: String = "",
    val priority: Int? = null,
    val vertFlex: IntRange = IntRange(0, 0),
    val horizFlex: IntRange = IntRange(0, 0),
) : IntrinsicMeasurable {
    override val parentData: Any
        get() = LayerModifierData(
            layoutPriority = priority,
        )

    override fun maxIntrinsicHeight(width: Int): Int {
        TODO("fallbackMeasure() not implemented")
    }

    override fun maxIntrinsicWidth(height: Int): Int {
        TODO("unused")
    }

    override fun minIntrinsicHeight(width: Int): Int {
        // vert flex
        return vertFlex.packedValue
    }

    override fun minIntrinsicWidth(height: Int): Int {
        // horiz flex
        return horizFlex.packedValue
    }
}
