/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.experiences.data

import android.graphics.Paint
import android.graphics.Typeface
import io.rover.sdk.core.data.domain.Color
import io.rover.sdk.core.data.domain.Font
import io.rover.sdk.core.data.domain.FontWeight
import io.rover.sdk.core.data.domain.TextAlignment
import io.rover.sdk.experiences.classic.asAndroidColor
import io.rover.sdk.experiences.classic.blocks.concerns.text.FontAppearance

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

internal fun FontWeight.mapToFont(): io.rover.sdk.experiences.classic.blocks.concerns.text.Font {
    return when (this) {
        // Refer to Android's frameworks/base's data/fonts.xml.  We are basically reversing the
        // aliases and filling in the gaps where a font weight is not available at all
        // (typically by rounding down).  Note that the typeface style
        // (Typeface.NORMAL/Typeface.BOLD, etc.) causes Android to use one of two behaviours:
        // for bold or regular, select font weight 400 (regular) or 700 (bold).  For all others,
        // it does the standard behaviour of adding 300 to the font weight.  We never need to
        // avail ourselves of that second behaviour; aliases are available for all the other
        // font weights, so we only will use Typeface.BOLD for selecting base bold (700).

        // 100 is aliased to sans-serif-thin, which is more like a 200.  Rounding up here.
        FontWeight.UltraLight -> io.rover.sdk.experiences.classic.blocks.concerns.text.Font(
            "sans-serif-thin",
            Typeface.NORMAL
        )

        // 200 is missing, but sans-serif-thin, the "100 weight" font is more like a 200 anyway.
        FontWeight.Thin -> io.rover.sdk.experiences.classic.blocks.concerns.text.Font(
            "sans-serif-thin",
            Typeface.NORMAL
        )

        // 300 is aliased to sans-serif-light.
        FontWeight.Light -> io.rover.sdk.experiences.classic.blocks.concerns.text.Font(
            "sans-serif-light",
            Typeface.NORMAL
        )

        // 400 is the default weight for sans-serif, no alias needed.
        FontWeight.Regular -> io.rover.sdk.experiences.classic.blocks.concerns.text.Font(
            "sans-serif",
            Typeface.NORMAL
        )

        // 500 is aliased to sans-serif-medium.
        FontWeight.Medium -> io.rover.sdk.experiences.classic.blocks.concerns.text.Font(
            "sans-serif-medium",
            Typeface.NORMAL
        )

        // 600 is missing.  We'll round it down to 500.
        FontWeight.SemiBold -> io.rover.sdk.experiences.classic.blocks.concerns.text.Font(
            "sans-serif-medium",
            Typeface.NORMAL
        )

        // 700 is standard bold, no alias needed.
        FontWeight.Bold -> io.rover.sdk.experiences.classic.blocks.concerns.text.Font("sans-serif", Typeface.BOLD)

        // 800 is missing.  We'll round down to standard bold.
        FontWeight.Heavy -> io.rover.sdk.experiences.classic.blocks.concerns.text.Font("sans-serif", Typeface.BOLD)

        // 900 is aliased to sans-serif-black.
        FontWeight.Black -> io.rover.sdk.experiences.classic.blocks.concerns.text.Font(
            "sans-serif-black",
            Typeface.NORMAL
        )
    }
}
