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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import io.rover.sdk.experiences.rich.compose.model.values.Action
import io.rover.sdk.experiences.rich.compose.ui.layout.SimpleMeasurePolicy

@SuppressLint("ModifierParameter")
@Composable
internal fun ActionModifier(
    action: Action?,
    modifier: Modifier,
    content: @Composable (modifier: Modifier) -> Unit
) {
    val onClick = createActionHandler(action)
    
    if (onClick != null) {
        ActionModifierButton(
            onClick = onClick,
            modifier = modifier
        ) {
            content(Modifier)
        }
    } else {
        content(modifier)
    }
}

@SuppressLint("ModifierParameter")
@Composable
private fun ActionModifierButton(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable (modifier: Modifier) -> Unit
) {
    Layout(
        content = { content(Modifier) },
        measurePolicy = SimpleMeasurePolicy(),
        modifier = modifier.clickable(
            onClick = onClick,
            role = Role.Button,
            interactionSource = remember { MutableInteractionSource() },
            indication = LocalIndication.current
        )
    )
}
