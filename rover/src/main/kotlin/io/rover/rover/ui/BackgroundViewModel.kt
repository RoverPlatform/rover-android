package io.rover.rover.ui

import io.rover.rover.core.domain.Background
import io.rover.rover.ui.views.asAndroidColor

/**
 * This interface is exposed by View Models that have support for a background.  Equivalent to
 * the [Background] domain model interface.
 */
interface BackgroundViewModelInterface {
    val backgroundColor: Int
}

open class BackgroundViewModel(
    val background: Background
): BackgroundViewModelInterface {
    override val backgroundColor: Int
        get() = background.backgroundColor.asAndroidColor()
}
