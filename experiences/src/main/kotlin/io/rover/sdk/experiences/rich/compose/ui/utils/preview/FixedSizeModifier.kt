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

package io.rover.sdk.experiences.rich.compose.ui.utils.preview

import android.util.Size
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import io.rover.sdk.experiences.rich.compose.ui.layout.mapMaxIntrinsicWidthAsMeasure
import io.rover.sdk.experiences.rich.compose.ui.layout.mapMinIntrinsicAsFlex

internal class FixedSizeModifier(
    private val width: Dp,
    private val height: Dp,
) : LayoutModifier {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(
            Constraints(maxWidth = width.roundToPx(), maxHeight = height.roundToPx()),
        )

        return layout(width.roundToPx(), height.roundToPx()) {
            placeable.place(
                (placeable.measuredWidth - placeable.width) / 2,
                (placeable.measuredHeight - placeable.height) / 2,
            )
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int {
        return mapMaxIntrinsicWidthAsMeasure(height) { proposedSize ->
            return@mapMaxIntrinsicWidthAsMeasure Size(this@FixedSizeModifier.width.roundToPx(), this@FixedSizeModifier.height.roundToPx())
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int {
        return mapMinIntrinsicAsFlex {
            IntRange(this@FixedSizeModifier.height.roundToPx(), this@FixedSizeModifier.height.roundToPx())
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int {
        return mapMinIntrinsicAsFlex {
            IntRange(this@FixedSizeModifier.width.roundToPx(), this@FixedSizeModifier.width.roundToPx())
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int {
        throw IllegalStateException("maxIntrinsicHeight not (yet) used in experiences intrinsics interface contract.")
    }
}
