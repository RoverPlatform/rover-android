package io.rover.rover.ui.views

import android.graphics.Rect
import io.rover.rover.ui.viewmodels.BackgroundViewModelInterface
import io.rover.rover.ui.viewmodels.BlockViewModelInterface
import io.rover.rover.ui.viewmodels.BorderViewModelInterface
import io.rover.rover.ui.viewmodels.ImageBlockViewModelInterface
import io.rover.rover.ui.viewmodels.TextBlockViewModelInterface

interface PaddingContributor {
    val contributedPadding: Rect
}

/**
 * Binds [BlockViewModelInterface] properties to that of a view.
 *
 * This is responsible for setting padding and anything else relating to block layout.
 */
interface ViewBlockInterface {
    var blockViewModel: BlockViewModelInterface?
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
