package io.rover.sdk.ui.blocks.concerns.border

import io.rover.sdk.data.domain.Border
import io.rover.sdk.ui.asAndroidColor

class BorderViewModel(
    val border: Border
) : BorderViewModelInterface {
    override val borderColor: Int
        get() = border.color.asAndroidColor()

    override val borderRadius: Int
        get() = border.radius

    override val borderWidth: Int
        get() = border.width
}
