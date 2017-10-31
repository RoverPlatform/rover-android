package io.rover.rover.ui.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import io.rover.rover.ui.viewmodels.LayoutableViewModel

/**
 * An Android view, as thin as possible,
 *
 * TODO: factor the "composability" concerns out into a base class.  Actually, MUST move everything
 * out because *duh* gotta be able to use any arbitrary android view base class.
 */
interface LayoutableView<VM: LayoutableViewModel> {
    var viewModel: VM?

    val view: View
        get() = this as View

    // TODO: maybe I put the composer here just to cut down on chatter passing it around?
}
