package io.rover.sdk.experiences.ui.blocks.concerns.border

import io.rover.sdk.core.data.domain.Border
import io.rover.sdk.experiences.ui.asAndroidColor

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
