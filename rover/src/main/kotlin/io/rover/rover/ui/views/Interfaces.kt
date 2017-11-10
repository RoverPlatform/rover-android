package io.rover.rover.ui.views

import android.graphics.Rect
import io.rover.rover.ui.viewmodels.BackgroundViewModelInterface
import io.rover.rover.ui.viewmodels.BlockViewModelInterface
import io.rover.rover.ui.viewmodels.BorderViewModelInterface
import io.rover.rover.ui.viewmodels.ImageBlockViewModelInterface
import io.rover.rover.ui.viewmodels.TextBlockViewModelInterface

/**
 * Binds [BlockViewModelInterface] properties to that of a view.
 *
 * This is responsible for setting padding and anything else relating to block layout.
 */
interface ViewBlockInterface {
    var blockViewModel: BlockViewModelInterface?

    /**
     * Other View* mixin classes can call this to contribute additional padding.
     *
     * TODO: this stateful approach currently requires an invariant that View mixins that contribute
     * padding must have their view model bound after ViewBlock.  Would be nice if there was
     * a firmer way to achieve this.
     */
    fun contributeAdditionalPadding(additionalPadding: Rect)
}

/**
 * Binds [BackgroundViewModelInterface] properties to that of a view.
 *
 * Backgrounds can specify a background colour or image.
 */
interface ViewBackgroundInterface {
    var backgroundViewModel: BackgroundViewModelInterface?
}

/**
 * Binds [BorderViewModelInterface] properties to that of a view.
 *
 * Borders can specify a border of arbitrary width, with optional rounded corners.
 */
interface ViewBorderInterface {
    var borderViewModel: BorderViewModelInterface?
}

interface ViewTextInterface {
    var textViewModel: TextBlockViewModelInterface?
}

interface ViewImageInterface {
    var imageViewModel: ImageBlockViewModelInterface?
}
