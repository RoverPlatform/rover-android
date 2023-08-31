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
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.rover.sdk.experiences.rich.compose.model.nodes.Node
import io.rover.sdk.experiences.rich.compose.model.values.ColorReference
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.layout.StripPackedIntrinsics
import io.rover.sdk.experiences.rich.compose.ui.modifiers.LayerModifiers
import io.rover.sdk.experiences.rich.compose.ui.values.getComposeColor

@Composable
internal fun IconLayer(node: io.rover.sdk.experiences.rich.compose.model.nodes.Icon, modifier: Modifier = Modifier) {
    IconLayer(
        node = node,
        materialName = node.icon.materialName,
        tint = node.color,
        iconSize = node.pointSize.dp,
        modifier = modifier
    )
}

@Composable
private fun IconLayer(node: Node, materialName: String, tint: ColorReference, iconSize: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resourceId: Int = context.getMaterialIconID(materialName)

    ApplyLayerModifiers(LayerModifiers(node), modifier) { modifier ->
        Icon(
            painter = painterResource(id = resourceId),
            tint = tint.getComposeColor(Environment.LocalIsDarkTheme.current),
            modifier = modifier.then(StripPackedIntrinsics()).size(iconSize),
            contentDescription = ""
        )
    }
}

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
