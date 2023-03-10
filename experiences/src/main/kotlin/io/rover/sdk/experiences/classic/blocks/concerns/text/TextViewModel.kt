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

package io.rover.sdk.experiences.classic.blocks.concerns.text

import android.graphics.Paint
import io.rover.sdk.core.data.domain.FontWeight
import io.rover.sdk.core.data.domain.Text
import io.rover.sdk.core.data.domain.TextAlignment
import io.rover.sdk.core.logging.log
import io.rover.sdk.experiences.classic.RectF
import io.rover.sdk.experiences.classic.asAndroidColor
import io.rover.sdk.experiences.data.mapToFont
import io.rover.sdk.experiences.services.MeasurementService

/**
 * Text styling and size concerns.
 */
internal class TextViewModel(
    private val styledText: Text,
    private val measurementService: MeasurementService,
    override val singleLine: Boolean = false,
    override val centerVertically: Boolean = false
) : TextViewModelInterface {
    override val text: String
        get() = styledText.rawValue

    override val fontAppearance: FontAppearance
        // this maps from the Rover font weight to a named font-family and typeface style,
        // which is what Android will ultimately expect since it doesn't explicitly support
        // a font weight.
        get() {
            val font = styledText.font.weight.mapToFont()

            return FontAppearance(
                styledText.font.size,
                font,
                styledText.color.asAndroidColor(),
                when (styledText.alignment) {
                    TextAlignment.Center -> Paint.Align.CENTER
                    TextAlignment.Left -> Paint.Align.LEFT
                    TextAlignment.Right -> Paint.Align.RIGHT
                }
            )
        }

    override fun boldRelativeToBlockWeight(): Font {
        FontWeight.values().lastIndex

        val addedOrdinal = styledText.font.weight.ordinal + 3
        val addedWeight = if (addedOrdinal <= FontWeight.values().lastIndex) {
            FontWeight.values()[addedOrdinal]
        } else {
            FontWeight.values().last()
        }

        return addedWeight.mapToFont()
    }

    override fun intrinsicHeight(bounds: RectF): Float {
        val width = if (bounds.width() < 0) {
            log.w("Bounds width somehow less than zero? Was ${bounds.width()}. Full bounds is $bounds. Bottoming out at zero.  Can happen if Fill block shrank down to zero on narrower screen than expected.")
            0f
        } else bounds.width()
        return measurementService.measureHeightNeededForRichText(
            if (singleLine) {
                // only measure a single line as configured.
                // However, as things stand, no single-line TextViewModels are actually measured, so
                // this case for intrinsicHeight() is only here for completeness.
                "1"
            } else {
                styledText.rawValue
            },
            fontAppearance,
            boldRelativeToBlockWeight(),
            width
        )
    }
}
