package io.rover.rover.ui.views

import io.rover.rover.ui.viewmodels.BackgroundViewModelInterface

/**
 * Binds [BackgroundViewModelInterface] properties to that of a view.
 */
interface ViewBackgroundInterface {
    var backgroundViewModel: BackgroundViewModelInterface?
}
