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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLifecycleOwner
import io.rover.sdk.experiences.rich.compose.model.nodes.Carousel
import io.rover.sdk.experiences.rich.compose.model.nodes.Collection
import io.rover.sdk.experiences.rich.compose.model.nodes.Conditional
import io.rover.sdk.experiences.rich.compose.model.nodes.Node
import io.rover.sdk.experiences.rich.compose.model.nodes.getItems
import io.rover.sdk.experiences.rich.compose.model.values.isSatisfied
import io.rover.sdk.experiences.rich.compose.ui.CarouselState
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.ViewID
import io.rover.sdk.experiences.rich.compose.ui.data.DataContext
import io.rover.sdk.experiences.rich.compose.ui.data.data
import io.rover.sdk.experiences.rich.compose.ui.data.makeDataContext
import io.rover.sdk.experiences.rich.compose.ui.data.urlParameters
import io.rover.sdk.experiences.rich.compose.ui.data.userInfo
import io.rover.sdk.experiences.rich.compose.ui.modifiers.LayerModifiers
import io.rover.sdk.experiences.rich.compose.ui.utils.ExpandMeasurePolicy
import io.rover.sdk.experiences.rich.compose.ui.utils.floorMod

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CarouselLayer(node: Carousel, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val collectionIndex = Environment.LocalCollectionIndex.current
    val viewID = ViewID(node.id, collectionIndex)

    val dataContext = makeDataContext(
        userInfo = Environment.LocalUserInfo.current?.invoke() ?: emptyMap(),
        urlParameters = Environment.LocalUrlParameters.current,
        data = Environment.LocalData.current
    )

    val collection = carouselPages(node, dataContext)
    val startIndex = if (node.isLoopEnabled) Int.MAX_VALUE / 2 else 0
    val pagerCount = if (node.isLoopEnabled) Int.MAX_VALUE else collection.size
    val pagerState = rememberPagerState(initialPage = startIndex)

    DisposableEffect(collection.size, lifecycleOwner) {
        Environment.LocalCarouselStates[viewID] =
            CarouselState(
                pagerState = pagerState,
                startIndex = startIndex,
                collectionSize = collection.size,
                isLoopEnabled = node.isLoopEnabled
            )
        onDispose {
            Environment.LocalCarouselStates.remove(viewID)
        }
    }
    ApplyLayerModifiers(layerModifiers = LayerModifiers(node), modifier = modifier) { modifier ->
        Layout(
            {
                HorizontalPager(
                    pageCount = pagerCount,
                    state = pagerState
                ) { index ->
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        val page = (index - startIndex).floorMod(collection.size)
                        CarouselPage(collection[page])
                    }
                }
            },
            modifier = modifier,
            measurePolicy = ExpandMeasurePolicy(false)
        )
    }
}

@Composable
private fun CarouselPage(carouselItem: CarouselItem) {
    if (carouselItem.item != null) {
        CompositionLocalProvider(Environment.LocalData provides carouselItem.item) {
            Children(listOf(carouselItem.node), modifier = Modifier)
        }
    } else {
        Children(listOf(carouselItem.node), modifier = Modifier)
    }
}

private data class CarouselItem(
    val node: Node,
    val item: Any? = null
)

private fun carouselPages(carousel: Carousel, dataContext: DataContext): List<CarouselItem> {
    fun generatePages(node: Node, dataContext: DataContext): List<CarouselItem> {
        return when (node) {
            is Collection -> {
                node.getItems(dataContext).flatMap { item ->
                    node.children.flatMap { childNode ->
                        val childDataContext = makeDataContext(
                                userInfo = dataContext.userInfo,
                                urlParameters = dataContext.urlParameters,
                                data = item
                        )

                        generatePages(childNode, childDataContext)
                    }
                }
            }
            is Conditional -> {
                if (!node.conditions.all { it.isSatisfied(dataContext) }) {
                    return emptyList()
                }

                node.children.flatMap { childNode ->
                    generatePages(childNode, dataContext)
                }
            }
            is Carousel -> {
                node.children.flatMap { childNode ->
                    generatePages(childNode, dataContext)
                }
            }
            else -> {
                listOf(CarouselItem(node, dataContext.data))
            }
        }
    }

    return generatePages(carousel, dataContext)
}
