package io.rover.rover.ui.views

import android.view.View
import io.rover.rover.ui.viewmodels.LayoutableViewModel

/**
 * Wraps a Rover Android [View] that can be laid out along with a possible view model that is bound
 * to it.
 *
 * This is usually implemented by the views themselves, and [view] just returns `this`.  This is an
 * interface rather than an abstract [View] subclass in order to allow implementers to inherit from
 * various different [View] subclasses.
 */
interface LayoutableView<VM : LayoutableViewModel> {
    var viewModel: VM?

    val view: View
        get() = this as View
}
