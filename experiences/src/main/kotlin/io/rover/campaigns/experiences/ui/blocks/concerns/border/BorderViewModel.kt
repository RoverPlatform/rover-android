package io.rover.campaigns.experiences.ui.blocks.concerns.border

import io.rover.campaigns.experiences.data.domain.Border
import io.rover.campaigns.experiences.ui.asAndroidColor

internal class BorderViewModel(
    val border: Border
) : BorderViewModelInterface {
    override val borderColor: Int
        get() = border.color.asAndroidColor()

    override val borderRadius: Int
        get() = border.radius

    override val borderWidth: Int
        get() = border.width
}
