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

package io.rover.sdk.experiences.rich.compose.ui.modifiers

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.rover.sdk.experiences.rich.compose.model.values.Point
import io.rover.sdk.experiences.rich.compose.ui.layout.PackedHeight

@SuppressLint("ModifierParameter")
@Composable
internal fun OffsetModifier(
    offset: Point?,
    modifier: Modifier,
    content: @Composable (modifier: Modifier) -> Unit
) {
    val localDensityContext = LocalDensity.current

    with(localDensityContext) {
        Layout({
            content(modifier)
        }, measurePolicy = offsetModifierMeasurePolicy(offset?.x?.dp?.roundToPx() ?: 0, offset?.y?.dp?.roundToPx() ?: 0))
    }
}

private fun offsetModifierMeasurePolicy(offsetX: Int, offsetY: Int): MeasurePolicy {
    return object : MeasurePolicy {

        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints
        ): MeasureResult {
            require(measurables.size == 1) {
                "OffsetModifierMeasurePolicy expects a single measurable being passed in. Received: $measurables"
            }

            val placeables = measurables.map { measurable ->
                measurable.measure(constraints)
            }

            val width = placeables.maxOf { it.measuredWidth }
            val height = placeables.maxOf { it.measuredHeight }

            return layout(width, height) {
                placeables.forEach { placeable ->
                    val widthDiff = (placeable.measuredWidth - placeable.width) / 2
                    val heightDiff = (placeable.measuredHeight - placeable.height) / 2
                    placeable.place(widthDiff + offsetX, heightDiff + offsetY)
                }
            }
        }

        // TODO: these default return values are a bit wrong.

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int
        ): Int {
            return measurables.firstOrNull()?.minIntrinsicWidth(height) ?: PackedHeight.Zero.packedValue
        }

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int
        ): Int {
            return measurables.firstOrNull()?.maxIntrinsicWidth(height) ?: PackedHeight.Zero.packedValue
        }

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int
        ): Int {
            return measurables.firstOrNull()?.minIntrinsicHeight(width) ?: PackedHeight.Zero.packedValue
        }

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int
        ): Int {
            return measurables.firstOrNull()?.maxIntrinsicHeight(width) ?: PackedHeight.Zero.packedValue
        }
    }
}
