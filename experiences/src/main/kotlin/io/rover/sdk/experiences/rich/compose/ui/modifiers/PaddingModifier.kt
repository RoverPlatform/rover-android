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

import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.rover.sdk.experiences.rich.compose.model.values.*
import io.rover.sdk.experiences.rich.compose.ui.layers.RectangleLayer
import io.rover.sdk.experiences.rich.compose.ui.layers.TextLayer
import io.rover.sdk.experiences.rich.compose.ui.layers.stacks.VStackLayer
import io.rover.sdk.experiences.rich.compose.ui.layout.*
import io.rover.sdk.experiences.rich.compose.ui.layout.fallbackMeasure
import io.rover.sdk.experiences.rich.compose.ui.layout.mapMaxIntrinsicWidthAsMeasure
import io.rover.sdk.experiences.rich.compose.ui.utils.unlessInfinity

@Composable
internal fun PaddingModifier(
    padding: Padding?,
    modifier: Modifier,
    content: @Composable (modifier: Modifier) -> Unit,
) {
    if (padding != null) {
        content(
            modifier
                .experiencesPadding(padding),
        )
    } else {
        content(modifier)
    }
}

private fun Modifier.experiencesPadding(padding: Padding) = this
    .then(ExperiencesPadding(padding))

private class ExperiencesPadding(val padding: Padding) : LayoutModifier {
    val horizontalPaddingValues get() = (padding.leading + padding.trailing).dp
    val verticalPaddingValues get() = (padding.top + padding.bottom).dp

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(
            Constraints(
                maxWidth = maxOf(constraints.maxWidth.unlessInfinity { it - horizontalPaddingValues.roundToPx() }, 0),
                maxHeight = maxOf(constraints.maxHeight.unlessInfinity { it - verticalPaddingValues.roundToPx() }, 0),
            ),
        )

        return layout(
            width = placeable.measuredWidth + horizontalPaddingValues.roundToPx(),
            height = placeable.measuredHeight + verticalPaddingValues.roundToPx(),
        ) {
            placeable.placeRelative(
                (placeable.measuredWidth - placeable.width) / 2 + padding.leading.dp.roundToPx(),
                (placeable.measuredHeight - placeable.height) / 2 + padding.top.dp.roundToPx(),
            )
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int {
        return mapMaxIntrinsicWidthAsMeasure(height) { (proposedWidth, proposedHeight) ->
            val childSize = measurable.fallbackMeasure(
                Size(
                    maxOf(proposedWidth.unlessInfinity { it - horizontalPaddingValues.roundToPx() }, 0),
                    maxOf(proposedHeight.unlessInfinity { it - verticalPaddingValues.roundToPx() }, 0),
                ),
            )

            Size(
                childSize.width.unlessInfinity { it + horizontalPaddingValues.roundToPx() } ,
                childSize.height.unlessInfinity { it + verticalPaddingValues.roundToPx() } ,
            )
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int {
        return mapMinIntrinsicAsFlex {
            // basically padding should be added to maximum flex. be careful of infinities when adding to maximum.
            val childFlex = measurable.experiencesHorizontalFlex()

            val lower = childFlex.first
            val upper = childFlex.last.unlessInfinity { it + horizontalPaddingValues.roundToPx() }

            IntRange(lower, upper)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int {
        return mapMinIntrinsicAsFlex {
            // basically padding should be added to maximum flex. be careful of infinities when adding to maximum.
            val childFlex = measurable.experiencesVerticalFlex()

            val lower = childFlex.first
            val upper = childFlex.last.unlessInfinity { it + verticalPaddingValues.roundToPx() }

            IntRange(lower, upper)
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int {
        throw IllegalStateException("Only call maxIntrinsicWidth, with packed parameter, on Rover Experiences measurables.")
    }
}

@Preview
@Composable
private fun TestWrapsLayer() {
    // test that the padding wraps the layer as expected (if unconstrained)
    PaddingModifier(padding = Padding(20f), modifier = Modifier) { modifier ->
        TextLayer(text = "Rover Rocks", modifier = modifier)
    }
}

@Preview
@Composable
private fun TestFitConstraints() {
    // this should show a 20x20 layer with no blue visible because all you can see
    // is the padding. Remember padding is applied before frame.
    PaddingModifier(padding = Padding(20f), modifier = Modifier.requiredSize(20.dp)) { modifier ->
        Box(
            modifier = modifier.background(Color.Blue),
        )
    }
}

// the same as the above, but using Frame modifier instead of requiredSize().
@Preview
@Composable
private fun IntegrationTestFitFrameConstraints() {
    RectangleLayer(
        fill = Fill.FlatFill(ColorReference.SystemColor("blue")),
        layerModifiers = LayerModifiers(
            padding = Padding(20f),
            frame = Frame(width = 20f, height = 20f, alignment = Alignment.CENTER),
        ),
    )
}

@Preview
@Composable
private fun PaddingInStack() {
    VStackLayer(
        spacing = 0f,
    ) {
        TextLayer(
            text = "Rover rocks",
            layerModifiers = LayerModifiers(
                padding = Padding(
                    20f,
                ),
            ),
        )
    }
}

@Preview
@Composable
private fun PaddingProposeSize() {
    ManipulateProposal { modifier ->
        TextLayer(text = "Hello", modifier = modifier.experiencesPadding(Padding(20f)))
    }
}

@Preview
@Composable
private fun PaddingAsLayerModifier() {
    TextLayer(text = "Hello", layerModifiers = LayerModifiers(
        padding = Padding(20f),
    ))
}
