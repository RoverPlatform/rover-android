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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.rover.sdk.experiences.rich.compose.model.nodes.Rectangle
import io.rover.sdk.experiences.rich.compose.model.values.*
import io.rover.sdk.experiences.rich.compose.model.values.Border
import io.rover.sdk.experiences.rich.compose.model.values.ColorReference
import io.rover.sdk.experiences.rich.compose.model.values.Fill
import io.rover.sdk.experiences.rich.compose.model.values.GradientReference
import io.rover.sdk.experiences.rich.compose.model.values.GradientValue
import io.rover.sdk.experiences.rich.compose.ui.modifiers.LayerModifiers
import io.rover.sdk.experiences.rich.compose.ui.modifiers.experiencesFrame
import io.rover.sdk.experiences.rich.compose.ui.utils.ExpandLayoutModifier
import io.rover.sdk.experiences.rich.compose.ui.utils.preview.InfiniteHeightMeasurePolicy
import io.rover.sdk.experiences.rich.compose.ui.values.getComposeBrush
import io.rover.sdk.experiences.rich.compose.ui.values.getComposeColor

@Composable
internal fun RectangleLayer(node: Rectangle, modifier: Modifier = Modifier) {
    RectangleLayer(modifier, fill = node.fill, border = node.border, cornerRadius = node.cornerRadius, layerModifiers = LayerModifiers(node))
}

@Composable
internal fun RectangleLayer(modifier: Modifier = Modifier, fill: Fill, border: Border? = null, cornerRadius: Float = 0f, layerModifiers: LayerModifiers = LayerModifiers()) {
    ApplyLayerModifiers(layerModifiers = layerModifiers, modifier = modifier) { modifier ->
        var size by remember { mutableStateOf(Size.Zero) }

        // compose modifier order: first in the list measures first, goes down. last is innermost. (opposite to swiftui)

        val borderModifier = if (border != null) {
            Modifier.border(width = border.width.dp, color = border.color.getComposeColor(), RoundedCornerShape(cornerRadius.dp))
        } else {
            Modifier
        }

        val fillModifier = when (fill) {
            is Fill.FlatFill -> {
                Modifier
                    .clip(RoundedCornerShape(cornerRadius.dp))
                    .background(fill.color.getComposeColor())
            }
            is Fill.GradientFill -> {
                Modifier
                    .clip(RoundedCornerShape(cornerRadius.dp))
                    .background(fill.gradient.getComposeBrush(size = size))
            }
        }

        // in order of who measures first: (so starting with outermost in SwiftUI terms)
        //  0: provided modifier
        //  1: border
        //  2. fill
        //  3. expansion
        //  4. globally positioned size tracker thingy for gradients (in swiftui this would go first but after expansion we're in compose mode and expansion is forcing this oe big)
        //  5: box

        val rectangleModifier = modifier
            .then(ExpandLayoutModifier(expandChildren = true))
            // everything below this point (innermost) is in Jetpack Compose measurement behaviour,
            // no packed intrinsics.  So having the fixed frame expand the Jetpack Compose border
            // and fill modifier. FIll modifier particularly is problematic when packed intrinsics
            // pass through, so having it on this side of Expand.
            .then(borderModifier)
            .then(fillModifier)
            .then(Modifier.onGloballyPositioned { coordinates -> size = coordinates.size.toSize() })

        Box(modifier = rectangleModifier)
    }
}

@Preview
@Composable
private fun RectanglePreview() {
    RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("blue")), cornerRadius = 20f)
}

@Preview
@Composable
private fun RectangleBorderPreview() {
    RectangleLayer(
        fill = Fill.FlatFill(ColorReference.SystemColor("blue")),
        border = Border(ColorReference.SystemColor("green"), 2f),
        cornerRadius = 20f,
    )
}

@Preview
@Composable
private fun RectangleLayerInInfinity() {
    Layout(
        measurePolicy = InfiniteHeightMeasurePolicy,
        content = {
            RectangleLayer(fill = Fill.FlatFill(ColorReference.SystemColor("blue")), cornerRadius = 20f)
        },
    )
}

@Preview
@Composable
private fun RectangleGradientPreview() {
    // Note: gradients require runtime behaviour so this preview
    // will only work when you press the little green play button
    // to run it on emulator or real device.
    RectangleLayer(
        fill = Fill.GradientFill(
            gradient = GradientReference.CustomGradient(
                GradientValue(
                    to = listOf(0.0f, 1.0f),
                    from = listOf(1.0f, 0.0f),
                    stops = listOf(
                        GradientStop(0.0f, ColorValue(1.0f, 1.0f, 0.0f, 0.0f)),
                        GradientStop(1.0f, ColorValue(1.0f, 0.0f, 0.0f, 1.0f)),
                    ),
                ),
            ),
        ),
        cornerRadius = 20f,
    )
}

@Preview
@Composable
private fun RectangleWithAppliedFrameIntegrationTest() {
    RectangleLayer(
        fill = Fill.FlatFill(ColorReference.SystemColor("blue")),
        cornerRadius = 20f,
        modifier = Modifier.experiencesFrame(
            Frame(width = 100f, height = 100f, alignment = Alignment.TOP_LEADING),
        ),
    )
}

@Preview
@Composable
private fun RectangleWithFrameModifierIntegrationTest() {
    RectangleLayer(
        fill = Fill.FlatFill(ColorReference.SystemColor("blue")),
        cornerRadius = 20f,
        layerModifiers = LayerModifiers(
            frame = Frame(width = 100f, height = 100f, alignment = Alignment.TOP_LEADING),
        ),
    )
}
