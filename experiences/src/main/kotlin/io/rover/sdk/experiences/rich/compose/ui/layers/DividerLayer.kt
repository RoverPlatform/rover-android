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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.rover.sdk.experiences.rich.compose.model.values.Axis
import io.rover.sdk.experiences.rich.compose.model.values.ColorReference
import io.rover.sdk.experiences.rich.compose.model.values.Fill
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.layers.stacks.HStackLayer
import io.rover.sdk.experiences.rich.compose.ui.layers.stacks.VStackLayer
import io.rover.sdk.experiences.rich.compose.ui.modifiers.LayerModifiers
import io.rover.sdk.experiences.rich.compose.ui.utils.ExpandLayoutModifier
import io.rover.sdk.experiences.rich.compose.ui.values.getComposeColor

@Composable
internal fun DividerLayer(node: io.rover.sdk.experiences.rich.compose.model.nodes.Divider) {
    DividerLayer(
        color = node.backgroundColor.getComposeColor(Environment.LocalIsDarkTheme.current),
        layerModifiers = LayerModifiers(node)
    )
}

@Composable
private fun DividerLayer(
    axis: Axis = Environment.LocalStackAxis.current ?: Axis.HORIZONTAL,
    color: Color = ColorReference.SystemColor("separator").getComposeColor(Environment.LocalIsDarkTheme.current),
    layerModifiers: LayerModifiers = LayerModifiers()
) {
    LayerBox(layerModifiers) {
        Divider(
            color = color,
            modifier = ExpandLayoutModifier(true, axis.rotate(), otherAxisSize = 1.dp)
        )
    }
}

/**
 * When in a vertical stack, we want a divider that expands horizontally, and vice versa.
 */
private fun Axis.rotate(): Axis {
    return when (this) {
        Axis.HORIZONTAL -> Axis.VERTICAL
        Axis.VERTICAL -> Axis.HORIZONTAL
    }
}

@Composable
private fun LabelledPreview(
    name: String,
    contents: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.padding(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(name, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.zIndex(10f))
            Box(
                modifier = Modifier.border(2.dp, color = Color.Magenta)
            ) {
                contents()
            }
        }
    }
}

@Preview(name = "Horizontal Divider", showBackground = true)
@Composable
private fun HorizontalDividerPreview() {
    LabelledPreview("Horizontal Divider") {
        VStackLayer() {
            RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("red")))
            DividerLayer()
            RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("blue")))
        }
    }
}

@Preview(name = "Vertical Divider", showBackground = true)
@Composable
private fun VerticalDividerPreview() {
    LabelledPreview("Vertical Divider") {
        HStackLayer() {
            RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("red")))
            DividerLayer()
            RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("blue")))
        }
    }
}
