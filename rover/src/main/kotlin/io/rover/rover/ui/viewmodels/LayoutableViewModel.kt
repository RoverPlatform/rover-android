package io.rover.rover.ui.viewmodels

import android.graphics.Rect
import io.rover.rover.ui.types.ViewType

/**
 * Any View Model that implements this interface can be laid out in in the vertically
 * scrollable plane of a [ScreenViewModel].
 */
interface LayoutableViewModel {
    // TODO: in here go parameters common to the display of both Rows and Blocks.  So, possibly,
    // insets, backgrounds, and colours.  Basically, these ViewModels will
    // wrap the [Background] and [Border] model interfaces.

    /**
     * Returns the position (within the bounds, particularly the width) that this view model should
     * be laid out.  Note that the returned rect is relative to the same space as the given [bounds]
     * rect, but not relative to the [bounds] rect itself.
     */
    fun frame(bounds: Rect): Rect

    /**
     * There is a constrained set of classes that will implement [LayoutableViewModel].
     * [BlockAndRowRecyclerAdapter] will need to know which type is associated with a given
     * instance of [LayoutableViewModel] in its render list.
     */
    val viewType: ViewType
}
