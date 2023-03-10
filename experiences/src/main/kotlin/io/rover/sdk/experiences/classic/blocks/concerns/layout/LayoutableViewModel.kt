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

package io.rover.sdk.experiences.classic.blocks.concerns.layout

import io.rover.sdk.experiences.classic.Rect
import io.rover.sdk.experiences.classic.RectF
import io.rover.sdk.experiences.classic.concerns.BindableViewModel
import io.rover.sdk.experiences.classic.layout.BlockAndRowRecyclerAdapter
import io.rover.sdk.experiences.classic.layout.ViewType
import io.rover.sdk.experiences.classic.layout.row.RowViewModel
import io.rover.sdk.experiences.classic.layout.screen.ScreenViewModel

/**
 * Any View Model that implements this interface can be laid out in in the vertically
 * scrollable plane of a [ScreenViewModel].
 *
 * (View models that do not implement this interface are typically used in a compositional way
 * as a part of other view models that do implement [LayoutableViewModel].
 */
internal interface LayoutableViewModel : BindableViewModel {
    /**
     * Measures and returns a [RectF] of the placement the view model (with origin being the
     * bounds).
     *
     * Note that the returned rect is relative to the same space as the given [bounds]
     * rect, but not relative to the [bounds] rect itself.
     *
     * Note that some types (particularly, [RowViewModel] will not honour all the given
     * constraints, particularly on the [Rect.bottom] element of the bounds as they define
     * their own height or expand to accommodate measured content as per the stacking/auto-height
     * feature.
     */
    fun frame(bounds: RectF): RectF

    /**
     * There is a constrained set of classes that will implement [LayoutableViewModel].
     * [BlockAndRowRecyclerAdapter] will need to know which type is associated with a given
     * instance of [LayoutableViewModel] in its render list.
     */
    val viewType: ViewType
}
