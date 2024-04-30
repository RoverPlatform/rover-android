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

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import io.rover.sdk.experiences.platform.LocalStorage
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
import io.rover.sdk.experiences.rich.compose.ui.data.device
import io.rover.sdk.experiences.rich.compose.ui.data.makeDataContext
import io.rover.sdk.experiences.rich.compose.ui.data.urlParameters
import io.rover.sdk.experiences.rich.compose.ui.data.userInfo
import io.rover.sdk.experiences.rich.compose.ui.modifiers.LayerModifiers
import io.rover.sdk.experiences.rich.compose.ui.utils.ExpandMeasurePolicy
import io.rover.sdk.experiences.rich.compose.ui.utils.floorMod
import io.rover.sdk.experiences.rich.compose.vendor.compose.ui.clip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CarouselLayer(node: Carousel, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val collectionIndex = Environment.LocalCollectionIndex.current
    val viewID = ViewID(node.id, collectionIndex)

    // used for storing carousel page when isRememberPositionEnabled is set
    val carouselIdentifier = "${Environment.LocalExperienceId.current ?: "local"}-${viewID}"

    val dataContext = makeDataContext(
        userInfo = Environment.LocalUserInfo.current?.invoke() ?: emptyMap(),
        urlParameters = Environment.LocalUrlParameters.current,
        data = Environment.LocalData.current,
        deviceContext = Environment.LocalDeviceContext.current
    )

    val collection = carouselPages(node, dataContext)
    val startIndex = if (node.isLoopEnabled) Int.MAX_VALUE / 2 else 0
    val pagerCount = if (node.isLoopEnabled) Int.MAX_VALUE else collection.size
    val pagerState = rememberPagerState(initialPage = startIndex) { pagerCount }

    // shared preferences, used for restoring carousel position
    val context = LocalContext.current
    val storage = remember { LocalStorage(context).getKeyValueStorageFor("remembered_carousel_positions") }

    val carouselState = remember {
        CarouselState(
            pagerState = pagerState,
            startIndex = startIndex,
            collectionSize = collection.size,
            carousel = node
        )
    }

    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(collection.size, lifecycleOwner) {
        // set up a Carousel State while this composable is in scope
        Environment.LocalCarouselStates[viewID] = carouselState

        // restore page as long as pages are present (which may not yet be the case if the carousel
        // contains a data source)
        if (collection.isNotEmpty() && node.isStoryStyleEnabled) {
            val previousPage = storage.getInt(carouselIdentifier)
            Log.d("Rover carousel", "Restoring carousel position to $previousPage for carousel ID: $carouselIdentifier")
            if (previousPage != 0) {
                coroutineScope.launch {
                    pagerState.scrollToPage(previousPage)
                }
            }
        }

        onDispose {
            Environment.LocalCarouselStates.remove(viewID)
        }
    }

    ApplyLayerModifiers(layerModifiers = LayerModifiers(node), modifier = modifier) { modifier ->
        LaunchedEffect(key1 = carouselState.anyMediaLayersPresent, pagerState.currentPage) {
            val startTime = Date()
            val page = pagerState.currentPage

            if (!node.isStoryStyleEnabled) {
                return@LaunchedEffect
            }
            val dueTime = startTime.time + node.storyAutoAdvanceDuration * 1000L
            val dueDate = Date(dueTime)
            while (true) {
                if (!carouselState.anyMediaLayersPresent) {
                    carouselState.updateProgress(page, ((Date().time - startTime.time) / (node.storyAutoAdvanceDuration * 1000L).toFloat()))
                }

                if (dueDate.before(Date()) && !carouselState.anyMediaLayersPresent) {
                    // this will be cancelled if the LaunchedEffect's key input -- anyMediaLayersPresent -- changes.
                    coroutineScope.launch {
                        // in order to avoid having the animation get cancelled in the midst of running,
                        // we'll dispatch this as a separate coroutine.
                        carouselState.animateAutoAdvance()
                    }
                    break
                }

                delay(100L)
            }
        }

        if(node.isStoryStyleEnabled) {
            DisposableEffect(key1 = pagerState.currentPage) {
                // use view id and store
                storage[carouselIdentifier] = pagerState.currentPage
                Log.d("Rover carousel", "Storing carousel position ${pagerState.currentPage} for view ID: $carouselIdentifier")

                onDispose {
                    // no-op
                }
            }
        }

        Layout(
            {
                val animationScope = rememberCoroutineScope()

                var pageSize by remember { mutableStateOf<IntSize?>(null) }

                val carouselModifier = Modifier.onGloballyPositioned { coordinates ->
                    pageSize = coordinates.size
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = carouselModifier
                ) { index ->
                    val tapToAdvanceModifier = if(node.isStoryStyleEnabled) {
                        Modifier
                            .pointerInput(pageSize) {
                                detectTapGestures { offset ->
                                    val pageWidth = pageSize?.width
                                    if (pageWidth == null) {
                                        Log.w("Rover carousel", "Couldn't determine page width, aborting tap-to-advance gesture processing.")
                                        return@detectTapGestures
                                    }

                                    val widthPercent = offset.x / pageWidth
                                    Log.d("Rover carousel", "Clicked on page $index at width percent of $widthPercent")
                                    if (widthPercent > 0.15) {
                                        animationScope.launch {
                                            pagerState.animateScrollToPage(index + 1)
                                        }
                                    } else {
                                        animationScope.launch {
                                            pagerState.animateScrollToPage(index - 1)
                                        }
                                    }
                                }
                            }
                    } else Modifier

                    val pageModifier = tapToAdvanceModifier
                        // prevent overdrawing children (Jetpack compose always adds any
                        // overdraw to child's size) from causing jank in the pager.
                        .clip(RectangleShape)
                        .fillMaxSize()

                    Box(contentAlignment = Alignment.Center, modifier = pageModifier) {
                        val page = (index - startIndex).floorMod(collection.size)

                        val pageVisible = pagerState.currentPage == index

                        CompositionLocalProvider(
                            Environment.LocalCarouselState provides Environment.LocalCarouselStates[viewID],
                            Environment.LocalCarouselInHiddenPage provides !pageVisible,
                            Environment.LocalCarouselPageNumber provides page,
                            // normally set by CollectionLayer view, however, since we visit
                            // the children directly with data context in the carouselPages()
                            // method rather than rely on Compose, CollectionLayer isn't used
                            // so it needs to be set here.
                            Environment.LocalCollectionIndex provides index,
                        ) {
                            CarouselPage(collection[page])
                        }
                    }
                }
            },
            modifier = modifier,
            measurePolicy = ExpandMeasurePolicy(false),
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
    val item: Any? = null,
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
                            deviceContext = dataContext.device,
                            data = item,
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
