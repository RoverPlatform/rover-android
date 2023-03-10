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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.rover.sdk.experiences.rich.compose.model.nodes.Spacer
import io.rover.sdk.experiences.rich.compose.model.values.Axis
import io.rover.sdk.experiences.rich.compose.model.values.ColorReference
import io.rover.sdk.experiences.rich.compose.model.values.Fill
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.layers.stacks.HStackLayer
import io.rover.sdk.experiences.rich.compose.ui.modifiers.LayerModifiers
import io.rover.sdk.experiences.rich.compose.ui.utils.ExpandMeasurePolicy
import io.rover.sdk.experiences.rich.compose.ui.utils.preview.InfiniteHeightMeasurePolicy

@Composable
internal fun SpacerLayer(node: Spacer) {
    SpacerLayer(modifier = Modifier, LayerModifiers(node))
}

@Composable
internal fun SpacerLayer(modifier: Modifier = Modifier, layerModifiers: LayerModifiers = LayerModifiers()) {
    LayerBox(layerModifiers.copy(layoutPriority = layerModifiers.layoutPriority ?: -1), modifier = modifier) {
        val stackAxis = Environment.LocalStackAxis.current ?: Axis.VERTICAL
        // note: Experiences spacer differs a bit from SwiftUI's default Spacer. Infinity default
        // size is 0 instead of 10.
        Layout(
            {},
            modifier = Modifier,
            measurePolicy = ExpandMeasurePolicy(expandChildren = false, axis = stackAxis, infinityDefault = 0.dp)
        )
    }
}

@Preview
@Composable
private fun SpacerInfinity() {
    Layout(
        {
            SpacerLayer()
        },
        measurePolicy = InfiniteHeightMeasurePolicy
    )
}

@Preview
@Composable
private fun IntegrationSpacerAndStack() {
    HStackLayer() {
        SpacerLayer()
        TextLayer("Rover Rocks")
    }
}

@Preview
@Composable
private fun IntegrationSpacerAndStackLosesToRectangle() {
    // confirms the layout priority is working correctly.
    HStackLayer() {
        RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("blue")))
        SpacerLayer()
        TextLayer("Rover Rocks")
    }
}
