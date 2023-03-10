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

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.rover.sdk.experiences.rich.compose.model.nodes.PageControl
import io.rover.sdk.experiences.rich.compose.model.values.PageControlStyle
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.ViewID
import io.rover.sdk.experiences.rich.compose.ui.layout.StripPackedIntrinsics
import io.rover.sdk.experiences.rich.compose.ui.modifiers.LayerModifiers
import io.rover.sdk.experiences.rich.compose.ui.values.getComposeColor
import io.rover.sdk.experiences.rich.compose.vendor.accompanist.pager.*

@OptIn(ExperimentalPagerApi::class)
@Composable
internal fun PageControlLayer(node: PageControl) {
    val collectionIndex = Environment.LocalCollectionIndex.current
    val viewID = node.carouselID?.let { ViewID(it, collectionIndex) }

    Environment.LocalCarouselStates[viewID]?.let { carouselState ->
        LayerBox(layerModifiers = LayerModifiers(node)) {
            HorizontalPagerIndicator(
                pagerState = carouselState.pagerState,
                pageCount = carouselState.collectionSize,
                activeColor = node.activeColor(),
                inactiveColor = node.inactiveColor(),
                pageIndexMapping = { (it - carouselState.startIndex).floorMod(carouselState.collectionSize) },
                modifier = Modifier
                    .then(StripPackedIntrinsics())
            )
        }
    }
}

@Composable
internal fun PageControl.activeColor(): Color {
    return when (this.style) {
        is PageControlStyle.DefaultPageControlStyle -> {
            if (Environment.LocalIsDarkTheme.current) {
                Color(0xFFFFFFFF)
            } else {
                Color(0xFF000000)
            }
        }
        is PageControlStyle.CustomPageControlStyle -> {
            this.style.currentColor.getComposeColor(Environment.LocalIsDarkTheme.current)
        }
        is PageControlStyle.DarkPageControlStyle -> {
            Color(0xFF000000)
        }
        is PageControlStyle.ImagePageControlStyle -> {
            // TODO: https://github.com/judoapp/judo-compose-develop/issues/66
            this.style.currentColor.getComposeColor(Environment.LocalIsDarkTheme.current)
        }
        is PageControlStyle.InvertedPageControlStyle -> {
            if (Environment.LocalIsDarkTheme.current) {
                Color(0xFF000000)
            } else {
                Color(0xFFFFFFFF)
            }
        }
        is PageControlStyle.LightPageControlStyle -> {
            Color(0xFFFFFFFF)
        }
    }
}

@Composable
internal fun PageControl.inactiveColor(): Color {
    return when (this.style) {
        is PageControlStyle.DefaultPageControlStyle -> {
            if (Environment.LocalIsDarkTheme.current) {
                Color(0x4DFFFFFF)
            } else {
                Color(0x4D000000)
            }
        }
        is PageControlStyle.CustomPageControlStyle -> {
            this.style.normalColor.getComposeColor(Environment.LocalIsDarkTheme.current)
        }
        is PageControlStyle.DarkPageControlStyle -> {
            Color(0x4D000000)
        }

        is PageControlStyle.ImagePageControlStyle -> {
            // TODO: https://github.com/judoapp/judo-compose-develop/issues/66
            this.style.normalColor.getComposeColor(Environment.LocalIsDarkTheme.current)
        }
        is PageControlStyle.InvertedPageControlStyle -> {
            if (Environment.LocalIsDarkTheme.current) {
                Color(0x4D000000)
            } else {
                Color(0x4DFFFFFF)
            }
        }
        is PageControlStyle.LightPageControlStyle -> {
            Color(0x4DFFFFFF)
        }
    }
}
