package io.rover.sdk.platform

import android.graphics.Paint
import io.rover.sdk.data.domain.Color
import io.rover.sdk.data.domain.Font
import io.rover.sdk.data.domain.TextAlignment
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.concerns.text.FontAppearance

internal fun TextAlignment.toPaintAlign(): Paint.Align {
    return when (this) {
        TextAlignment.Center -> Paint.Align.CENTER
        TextAlignment.Left -> Paint.Align.LEFT
        TextAlignment.Right -> Paint.Align.RIGHT
    }
}

internal fun Font.getFontAppearance(color: Color, alignment: TextAlignment): FontAppearance {
    val font = this.weight.mapToFont()

    return FontAppearance(this.size, font, color.asAndroidColor(), alignment.toPaintAlign())
}