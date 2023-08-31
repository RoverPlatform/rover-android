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
 * ApplyLayerModifiers handles the responsibility of applying the various Rover Experiences
 * modifiers to the composable content of a Layer.
 */
@Composable
internal fun ApplyLayerModifiers(layerModifiers: LayerModifiers, modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    val disableModifiers = false

    if (disableModifiers) {
        content(modifier)
        return
    }
    // Compose modifier order: first in the list measures first, goes down. last is innermost. (opposite to swiftui)

    // the list of modifiers is given below (in SwiftUI order, innermost first), to track the order thereof until they are all
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

    // We arrange these as this "pyramid" of nested composables, because some of the Experiences modifiers
    // need a full composable to implement, and cannot just be done as a Jetpack Compose modifier.

    val outermostModifier = Modifier.setLayerModifierData(
        layerModifiers.layoutPriority ?: 0,
        debugNode = layerModifiers.debugNode,
    ).then(modifier)

    // this outer SimpleMeasurementPolicy Layout is still required, corrects several layout issues.
    // it's likely there are several downstream bugs within the various modifiers in the Pyramid
    // below that this is working around.
    Layout(
        measurePolicy = SimpleMeasurePolicy(),
        modifier = outermostModifier,
        content = {
            // TODO: find a way to flatten out this nasty pyramid.
            // Disable name_shadowing warning, because naming each modifier lambda parameter is clunky.
            @Suppress("NAME_SHADOWING")
            OffsetModifier(
                offset = layerModifiers.offset,
                modifier = Modifier,
            ) { modifier ->
                ActionModifier(
                    action = layerModifiers.action,
                    modifier = modifier,
                ) { modifier ->
                    AccessibilityModifier(
                        accessibility = layerModifiers.accessibility,
                        modifier = modifier,
                    ) { modifier ->
                        MaskModifier(
                            mask = layerModifiers.mask,
                            modifier = modifier,
                        ) { modifier ->
                            OverlayModifier(
                                overlay = layerModifiers.overlay,
                                modifier = modifier,
                            ) { modifier ->
                                BackgroundModifier(
                                    background = layerModifiers.background,
                                    modifier = modifier,
                                ) { modifier ->
                                    OpacityModifier(
                                        opacity = layerModifiers.opacity,
                                        modifier = modifier,
                                    ) { modifier ->
                                        FrameModifier(
                                            frame = layerModifiers.frame,
                                            modifier = modifier,
                                        ) { modifier ->
                                            PaddingModifier(
                                                padding = layerModifiers.padding,
                                                modifier = modifier,
                                            ) { modifier ->
                                                ExperiencesShadowModifier(
                                                    shadow = layerModifiers.shadow,
                                                    modifier = modifier,
                                                ) { modifier ->
                                                    AspectRatioModifier(
                                                        aspectRatio = layerModifiers.aspectRatio,
                                                        modifier = modifier,
                                                    ) { modifier ->
                                                        content(modifier)
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
    )
}
