package io.rover.rover.ui.views

import io.rover.rover.ui.viewmodels.BackgroundViewModelInterface
import io.rover.rover.ui.viewmodels.BorderViewModelInterface

/**
 * Binds [BackgroundViewModelInterface] properties to that of a view.
 */
interface ViewBackgroundInterface {
    var backgroundViewModel: BackgroundViewModelInterface?
}

/**
 * Binds [BorderViewModelInterface] properties to that of a view.
 */
interface ViewBorderInterface {
    var borderViewModel: BorderViewModelInterface?
}
