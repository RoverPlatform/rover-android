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

package io.rover.sdk.experiences.rich.compose.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.rover.sdk.core.logging.log
import io.rover.sdk.experiences.rich.compose.model.nodes.Carousel
import io.rover.sdk.experiences.rich.compose.ui.utils.floorMod

@OptIn(ExperimentalFoundationApi::class)
internal data class CarouselState(
    val pagerState: PagerState,
    val startIndex: Int,
    val collectionSize: Int,
    val carousel: Carousel,
) {
    private var visibleMediaPlayers: Set<ViewID> by mutableStateOf(emptySet())

    /**
     * progress of a given page, mutable state used because it is observed in compose-land.
     */
    private var currentMediaProgress: Map<Int, Float> by mutableStateOf(emptyMap())

    suspend fun animateAutoAdvance() {
        if (!carousel.isStoryStyleEnabled) {
            return
        }

        val page = (pagerState.currentPage - startIndex).floorMod(collectionSize)

        if (page + 1 < collectionSize) {
            log.d("Auto-advancing carousel to next page.")
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        } else {
            log.d("Auto-advancing carousel reached the end, doing nothing.")
        }
    }

    fun mediaLayerAppeared(viewID: ViewID) {
        visibleMediaPlayers += viewID
        log.d("Media layer $viewID reports appearing, now at ${visibleMediaPlayers.count()} layers")
    }

    fun mediaLayerDisappeared(viewID: ViewID) {
        visibleMediaPlayers -= viewID
        log.d("Media layer $viewID reports disappearing, now at ${visibleMediaPlayers.count()} layers")
    }

    fun updateProgress(pageIndex: Int, progress: Float) {
        this.currentMediaProgress = currentMediaProgress + (pageIndex to progress)
    }

    fun progressFor(pageIndex: Int): Float? {
        return currentMediaProgress[pageIndex]
    }

    /**
     * Are there any media layers present in the carousel?
     *
     * (Note: can be safely observed from Composables, because it is backed by mutableStateOf).
     */
    val anyMediaLayersPresent: Boolean
        get() = visibleMediaPlayers.isNotEmpty()
}
