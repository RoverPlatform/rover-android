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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.rover.sdk.experiences.rich.compose.model.nodes.Node
import io.rover.sdk.experiences.rich.compose.model.nodes.Rectangle
import io.rover.sdk.experiences.rich.compose.model.values.Fill
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.values.getComposeColor
import io.rover.sdk.experiences.rich.compose.vendor.compose.ui.alpha
import io.rover.sdk.experiences.rich.compose.vendor.compose.ui.clip

@Composable
internal fun MaskModifier(
    mask: Node?,
    modifier: Modifier,
    content: @Composable (modifier: Modifier) -> Unit,
) {
    if (mask != null) {
        content(
            modifier
                .experiencesMask(mask, Environment.LocalIsDarkTheme.current),
        )
    } else {
        content(modifier)
    }
}

private fun Modifier.experiencesMask(mask: Node, isDarkMode: Boolean) = this
    .then(
        if (mask is Rectangle) {
            // Using vendored version of .clip here to enable Packed Intrinsics passthrough.
            var modifier = Modifier.clip(RoundedCornerShape(mask.cornerRadius.dp))
            modifier = when (mask.fill) {
                is Fill.FlatFill -> {
                    modifier.alpha(mask.fill.color.getComposeColor(isDarkMode).alpha)
                }

                is Fill.GradientFill -> {
                    modifier.alpha(1.0f)
                }
            }
            modifier
        } else {
            Modifier
        },
    )
