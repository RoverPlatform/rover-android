package io.rover.experiences.ui.layout.screen

import io.rover.core.data.domain.Experience
import io.rover.core.data.domain.Screen
import io.rover.experiences.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.experiences.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.experiences.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.core.ui.concerns.BindableViewModel
import io.rover.experiences.ui.layout.BlockAndRowLayoutManager
import io.rover.experiences.ui.layout.row.RowViewModelInterface
import io.rover.experiences.ui.toolbar.ToolbarConfiguration
import io.rover.experiences.ui.layout.Layout
import io.rover.experiences.ui.navigation.NavigateTo
import org.reactivestreams.Publisher

/**
 * View Model for a Screen.  Used in [Experience]s.
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
interface ScreenViewModelInterface : BindableViewModel, BackgroundViewModelInterface {
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

    data class Event(
        val rowId: String,
        val blockId: String,
        val navigateTo: NavigateTo
    )
}