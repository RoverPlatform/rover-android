package io.rover.rover.ui

import android.graphics.Rect

/**
 * Any View Model that implements this interface can be laid out in the
 */
interface LayoutableViewModel {
    // TODO: in here go parameters common to the display of both Rows and Blocks.  So, possibly,
    // and and insets/outsets, backgrounds, and colours.  Basically, these ViewModels will
    // wrap the [Background] and [Border] model interfaces.

    fun frame(bounds: Rect): Rect
}
