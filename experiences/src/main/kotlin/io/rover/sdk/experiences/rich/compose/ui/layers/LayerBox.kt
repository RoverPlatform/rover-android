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
import io.rover.sdk.experiences.rich.compose.ui.modifiers.*
import io.rover.sdk.experiences.rich.compose.ui.utils.SimpleMeasurePolicy

/**
 * LayerBox is responsible for two tasks:
 *
 * - Applying the Experiences modifiers, which are themselves implemented as full composables.
 * - Nesting each layer composable within a box that disables the default Jetpack Compose
 * intrinsic measurements.
 *
 * Note that LayerBox must be used *within* each of the layer composables, and not without.
 *
 * Also note that LayerBox should be the outermost composable on a given layer. This is to be
 * sure that layout priority is surfaced correctly on the IntrinsicMeasurable offered up to
 * any containing stacks.
 *
 * This is because the Layer Box must be excluded in the event of one of those composables yielding
 * empty (such as in the event of interpolation failure), or certain other composables that do
 * not participate in layout (data source, collection, conditional, etc.) being able to exclude it.
 *
 * (In SwiftUI this approach was not needed, because modifiers when applied to EmptyView have no
 * effect.)
 */
@Composable
internal fun LayerBox(layerModifiers: LayerModifiers, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // the list of modifiers is given below, to track the order thereof until they are all
    // implemented:

    // aspect ratio modifier
    // padding modifier
    // frame modifier
    // layout priority modifier* (this one down outside in order to expose the measurable
    //                            parentData - but the ordering difference has no effect in this
    //                            case.)
    // shadow modifier
    // opacity modifier
    // background modifier
    // overlay modifier
    // mask modifier
    // accessibility modifier
    // action modifier
    // offset modifier
    // TODO: safe area modifier

    // TODO: find a way to flatten out this nasty pyramid.
    // Disable name_shadowing warning, because naming each modifier lambda parameter is clunky.
    @Suppress("NAME_SHADOWING")
    Layout(
        {
            OffsetModifier(
                offset = layerModifiers.offset,
                modifier = Modifier
            ) { modifier ->
                ActionModifier(
                    action = layerModifiers.action,
                    modifier = modifier
                ) { modifier ->
                    AccessibilityModifier(
                        accessibility = layerModifiers.accessibility,
                        modifier = modifier
                    ) { modifier ->
                        MaskModifier(
                            mask = layerModifiers.mask,
                            modifier = modifier
                        ) { modifier ->
                            OverlayModifier(
                                overlay = layerModifiers.overlay,
                                modifier = modifier
                            ) { modifier ->
                                BackgroundModifier(
                                    background = layerModifiers.background,
                                    modifier = modifier
                                ) { modifier ->
                                    OpacityModifier(
                                        opacity = layerModifiers.opacity,
                                        modifier = modifier
                                    ) { modifier ->
                                        FrameModifier(
                                            frame = layerModifiers.frame,
                                            modifier = modifier
                                        ) { modifier ->
                                            PaddingModifier(
                                                padding = layerModifiers.padding,
                                                modifier = modifier
                                            ) { modifier ->
                                                ExperiencesShadowModifier(
                                                    shadow = layerModifiers.shadow,
                                                    modifier = modifier
                                                ) { modifier ->
                                                    AspectRatioModifier(
                                                        aspectRatio = layerModifiers.aspectRatio,
                                                        modifier = modifier
                                                    ) { modifier ->
                                                        Layout(
                                                            {
                                                                content()
                                                            },
                                                            modifier = modifier,
                                                            measurePolicy = SimpleMeasurePolicy(traceName = layerModifiers.debugNode?.let { "${it.description}::measure" })
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier.setLayerModifierData(
            layerModifiers.layoutPriority ?: 0,
            debugNode = layerModifiers.debugNode
        ),
        measurePolicy = SimpleMeasurePolicy()
    )
}
