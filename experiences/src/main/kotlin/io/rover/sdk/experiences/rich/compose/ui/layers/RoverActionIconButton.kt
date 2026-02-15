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

import android.content.Context
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import io.rover.sdk.experiences.rich.compose.model.nodes.MenuItem
import io.rover.sdk.experiences.rich.compose.model.values.ColorReference
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.modifiers.createActionHandler
import io.rover.sdk.experiences.rich.compose.ui.values.getComposeColor

/**
 * A Material3 IconButton that executes a Rover Experience action.
 * 
 * This composable bridges Rover Experience actions to Material3 UI components,
 * handling icon resolution, action execution, and event tracking.
 * 
 * Intended for use in Material3 TopAppBars for pluggable navigation support.
 * 
 * @param menuItem The MenuItem containing the icon, action, and title
 * @param buttonColor The color reference for the icon tint
 */
@Composable
internal fun RoverActionIconButton(
    menuItem: MenuItem,
    buttonColor: ColorReference,
) {
    val context = LocalContext.current
    val isDarkTheme = Environment.LocalIsDarkTheme.current
    val iconResourceId = context.getMaterialIconID(menuItem.iconMaterialName)
    val actionHandler = createActionHandler(menuItem.action)
    
    IconButton(
        onClick = {
            actionHandler?.invoke()
        }
    ) {
        Icon(
            painter = painterResource(id = iconResourceId),
            contentDescription = menuItem.title,
            tint = buttonColor.getComposeColor(isDarkTheme)
        )
    }
}

/**
 * Helper extension to resolve Material icon names to Android drawable resource IDs.
 * 
 * Supports both standard icons and ".fill" variant icons from Rover's icon system.
 */
private fun Context.getMaterialIconID(iconName: String): Int {
    return if (iconName.endsWith(".fill")) {
        resources.getIdentifier(
            "rover_exp_baseline_${iconName.substringBeforeLast(".fill")}",
            "drawable",
            this.packageName
        )
    } else {
        resources.getIdentifier("rover_exp_$iconName", "drawable", this.packageName)
    }
}
