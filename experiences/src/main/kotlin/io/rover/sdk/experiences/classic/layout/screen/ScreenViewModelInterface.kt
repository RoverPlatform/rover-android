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

package io.rover.sdk.experiences.classic.layout.screen

import io.rover.sdk.core.data.domain.ClassicExperienceModel
import io.rover.sdk.core.data.domain.Row
import io.rover.sdk.core.data.domain.Screen
import io.rover.sdk.experiences.classic.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.experiences.classic.blocks.concerns.layout.BlockViewModelInterface
import io.rover.sdk.experiences.classic.blocks.concerns.layout.LayoutableViewModel
import io.rover.sdk.experiences.classic.concerns.BindableViewModel
import io.rover.sdk.experiences.classic.layout.BlockAndRowLayoutManager
import io.rover.sdk.experiences.classic.layout.Layout
import io.rover.sdk.experiences.classic.layout.row.RowViewModelInterface
import io.rover.sdk.experiences.classic.navigation.NavigateToFromBlock
import io.rover.sdk.experiences.classic.toolbar.ToolbarConfiguration
import org.reactivestreams.Publisher

/**
 * View Model for a Screen.  Used in [ClassicExperienceModel]s.
 *
 * Rover View Models are a little atypical compared to what you may have seen elsewhere in industry:
 * unusually, layouts are data, so much layout structure and parameters are data passed through and
 * transformed by the view models.
 *
 * Implementers can take a comprehensive UI layout contained within a Rover [Screen], such as that
 * within an Experience, and lay all of the contained views out into two-dimensional space.  It does
 * so by mapping a given [Screen] to an internal graph of [RowViewModelInterface]s and
 * [BlockViewModelInterface]s, ultimately yielding the [RowViewModelInterface]s and
 * [BlockViewModelInterface]s as a sequence of [LayoutableViewModel] flat blocks in two-dimensional
 * space.
 *
 * Primarily used by [BlockAndRowLayoutManager].
 */
internal interface ScreenViewModelInterface : BindableViewModel, BackgroundViewModelInterface {
    /**
     * Do the computationally expensive operation of laying out the entire graph of UI view models.
     */
    fun render(widthDp: Float): Layout

    /**
     * Retrieve a list of the view models in the order they'd be laid out (guaranteed to be in
     * the same order as returned by [render]), but without the layout itself being performed.
     */
    fun gather(): List<LayoutableViewModel>

    val rowViewModels: List<RowViewModelInterface>

    /**
     * Screens may emit navigation events.
     *
     * In particular it aggregates all the navigation events from the contained rows.
     */
    val events: Publisher<Event>

    val needsBrightBacklight: Boolean

    val appBarConfiguration: ToolbarConfiguration

    val screenId: String

    val screen: Screen

    data class Event(
        val rowId: String,
        val blockId: String,
        val navigateTo: NavigateToFromBlock,
        val row: Row
    )
}
