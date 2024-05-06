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

package io.rover.sdk.experiences.rich.compose.ui.layers

import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.rover.sdk.experiences.rich.compose.model.nodes.Audio
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.ViewID
import io.rover.sdk.experiences.rich.compose.ui.layout.mapMaxIntrinsicWidthAsMeasure
import io.rover.sdk.experiences.rich.compose.ui.layout.mapMinIntrinsicAsFlex
import io.rover.sdk.experiences.rich.compose.ui.modifiers.LayerModifiers

@Composable
internal fun AudioLayer(node: Audio, modifier: Modifier) {
    val playerHeight = 88.dp

    val collectionIndex = Environment.LocalCollectionIndex.current
    val viewID = ViewID(node.id, collectionIndex)

    MediaPlayer(
        source = node.source,
        looping = node.looping,
        autoPlay = node.autoPlay,
        showControls = true,
        timeoutControls = false,
        viewID = viewID,
        modifier = modifier,
        layerModifiers = LayerModifiers(node),
        measurePolicy = AudioMeasurePolicy(playerHeight)
    )
}

internal class AudioMeasurePolicy(
    private val playerHeight: Dp
) : MeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {
        val childConstraints = Constraints.fixed(
            constraints.maxWidth,
            playerHeight.roundToPx()
        )

        val placeables = measurables.map { measurable ->
            measurable.measure(childConstraints)
        }

        return layout(constraints.maxWidth, playerHeight.roundToPx()) {
            placeables.forEach { placeable ->
                placeable.place(0, 0)
            }
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int
    ): Int {
        return mapMaxIntrinsicWidthAsMeasure(height) { proposedSize ->
            Size(
                proposedSize.width,
                playerHeight.roundToPx()
            )
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int
    ): Int {
        return mapMinIntrinsicAsFlex {
            // completely inflexible on height.
            IntRange(playerHeight.roundToPx(), playerHeight.roundToPx())
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int
    ): Int {
        return mapMinIntrinsicAsFlex {
            // completely flexible on width.
            IntRange(0, Constraints.Infinity)
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int
    ): Int {
        throw IllegalStateException("Only call the Rover overloaded packed intrinsics methods on Rover measurables, maxIntrinsicHeight is not used")
    }
}
