package io.rover.experiences.ui.blocks.concerns.layout

import android.view.View
import io.rover.core.ui.concerns.BindableView

/**
 * Wraps a Rover Android [View] that can be laid out along with a possible view model that is bound
 * to it.
 *
 * This is usually implemented by the views themselves, and [view] just returns `this`.  This is an
 * interface rather than an abstract [View] subclass in order to allow implementers to inherit from
 * various different [View] subclasses.
 */
interface LayoutableView<VM : LayoutableViewModel> : BindableView<VM>
