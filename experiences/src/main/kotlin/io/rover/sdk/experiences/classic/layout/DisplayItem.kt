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

package io.rover.sdk.experiences.classic.layout

import io.rover.sdk.experiences.classic.RectF
import io.rover.sdk.experiences.classic.blocks.concerns.layout.LayoutableViewModel
import io.rover.sdk.experiences.classic.layout.screen.ScreenViewModel

/**
 * A sequence of [LayoutableViewModel]s in two-dimensional space, with optional clips,
 * as an output of a layout pass.
 */
internal data class DisplayItem(
    /**
     * Where in absolute space (both position and dimensions) for the entire [ScreenViewModel]
     * this item should be placed.
     */
    val position: RectF,

    /**
     * An optional clip area, in the coordinate space of the view itself (ie., top left of the view
     * is origin).
     */
    val clip: RectF?,

    /**
     * The view model itself that originally supplied the layout parameters and must also be
     * bound to the View at display time to set styling and display content.
     */
    val viewModel: LayoutableViewModel
)
