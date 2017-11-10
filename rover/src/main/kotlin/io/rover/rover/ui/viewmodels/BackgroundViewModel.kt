package io.rover.rover.ui.viewmodels

import io.rover.rover.core.domain.Background
import io.rover.rover.ui.views.asAndroidColor

class BackgroundViewModel(
    val background: Background
) : BackgroundViewModelInterface {
    override val backgroundColor: Int
        get() = background.backgroundColor.asAndroidColor()
}
