package io.rover.rover.ui.viewmodels

import android.graphics.RectF
import io.rover.rover.core.domain.Block
import io.rover.rover.core.domain.Border
import io.rover.rover.ui.views.asAndroidColor

open class BorderViewModel(
    val border: Border
): BorderViewModelInterface {
    override val borderColor: Int
        get() = border.borderColor.asAndroidColor()

    override val borderRadius: Int
        get() = border.borderRadius

    override val borderWidth: Int
        get() = border.borderWidth
}
