package io.rover.experiences.ui.blocks.concerns.border

import io.rover.experiences.data.domain.Border
import io.rover.experiences.ui.asAndroidColor

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
