package io.rover.rover.ui

import android.graphics.Rect

/**
 * Any View Model that implements this interface can be laid out in in the vertically
 * scrollable plane of a [ScreenViewModel].
 */
interface LayoutableViewModel {
    // TODO: in here go parameters common to the display of both Rows and Blocks.  So, possibly,
    // and and insets/outsets, backgrounds, and colours.  Basically, these ViewModels will
    // wrap the [Background] and [Border] model interfaces.

    /**
     * Returns the position (within the bounds, particularly the width) that this view model should
     * be laid out.  Note that the returned rect is relative to the same space as the given [bounds]
     * rect, but not relative to the [bounds] rect itself.
     */
    fun frame(bounds: Rect): Rect
}
