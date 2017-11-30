package io.rover.rover.ui.viewmodels

import io.rover.rover.core.domain.Border
import io.rover.rover.ui.types.Rect
import io.rover.rover.ui.views.asAndroidColor

class BorderViewModel(
    val border: Border
) : BorderViewModelInterface {
    override val borderColor: Int
        get() = border.borderColor.asAndroidColor()

    override val borderRadius: Int
        get() = border.borderRadius

    override val borderWidth: Int
        get() = border.borderWidth

    override val paddingDeflection: Rect
        get() = Rect(
            border.borderWidth,
            border.borderWidth,
            border.borderWidth,
            border.borderWidth
        )
}
