package io.rover.experiences.ui.blocks.concerns.border

import io.rover.experiences.ui.blocks.concerns.layout.Padding
import io.rover.core.data.domain.Border
import io.rover.core.ui.asAndroidColor

class BorderViewModel(
    val border: Border
) : BorderViewModelInterface {
    override val borderColor: Int
        get() = border.color.asAndroidColor()

    override val borderRadius: Int
        get() = border.radius

    override val borderWidth: Int
        get() = border.width

    override val paddingDeflection: Padding
        get() = Padding(
            border.width,
            border.width,
            border.width,
            border.width
        )
}
