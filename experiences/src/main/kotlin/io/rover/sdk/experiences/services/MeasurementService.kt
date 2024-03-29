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

package io.rover.sdk.experiences.services

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.util.DisplayMetrics
import io.rover.sdk.experiences.classic.blocks.barcode.BarcodeViewModelInterface
import io.rover.sdk.experiences.classic.blocks.concerns.text.Font
import io.rover.sdk.experiences.classic.blocks.concerns.text.FontAppearance
import io.rover.sdk.experiences.classic.blocks.concerns.text.RichTextToSpannedTransformer
import io.rover.sdk.experiences.classic.dpAsPx
import io.rover.sdk.experiences.classic.pxAsDp

internal class MeasurementService(
    private val displayMetrics: DisplayMetrics,
    private val richTextToSpannedTransformer: RichTextToSpannedTransformer,
    private val barcodeRenderingService: BarcodeRenderingService
) {
    // Snap the given Dp value to the nearest pixel, returning the equivalent Dp value.  The goal
    // here is to ensure when the resulting Dp value is converted to a Px value (for the same
    // density), no rounding will need to occur to yield an exact Px value (since Px values cannot
    // be fractional).  This is useful because we do not want to accrue rounding errors downstream.
    fun snapToPixValue(dpValue: Int): Float {
        return dpValue.dpAsPx(displayMetrics).pxAsDp(displayMetrics)
    }

    fun measureHeightNeededForMultiLineTextInTextView(
        text: String,
        fontAppearance: FontAppearance,
        width: Float,
        textViewLineSpacing: Float = 1.0f
    ): Float {
        val spanned = SpannableString(text)

        val paint = TextPaint().apply {
            textSize = fontAppearance.fontSize * displayMetrics.scaledDensity
            typeface = Typeface.create(fontAppearance.font.fontFamily, fontAppearance.font.fontStyle)
            textAlign = fontAppearance.align
        }

        val textLayoutAlign = when (fontAppearance.align) {
            Paint.Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            Paint.Align.LEFT -> Layout.Alignment.ALIGN_NORMAL
            Paint.Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }

        val layout = StaticLayout(
            spanned,
            paint,
            width.dpAsPx(displayMetrics),
            textLayoutAlign,
            textViewLineSpacing,
            0f,
            true
        )
        return layout.height.pxAsDp(displayMetrics)
    }

    /**
     * Measure how much height a given bit of Unicode [richText] (with optional HTML tags such as
     * strong, italic, and underline) will require if soft wrapped to the given [width] and
     * [fontAppearance] ultimately meant to be displayed using an Android View.
     *
     * [boldFontAppearance] provides any font size or font-family modifications that should be
     * applied to bold text ( with no changes to colour or alignment).  This allows for nuanced
     * control of bold span styling.
     *
     * Returns the height needed to accommodate the text at the given width, in dps.
     */
    @SuppressLint("NewApi")
    fun measureHeightNeededForRichText(
        richText: String,
        fontAppearance: FontAppearance,
        boldFontAppearance: Font,
        width: Float
    ): Float {
        val spanned = richTextToSpannedTransformer.transform(richText, boldFontAppearance)

        val paint = TextPaint().apply {
            textSize = fontAppearance.fontSize.toFloat() * displayMetrics.scaledDensity
            typeface = Typeface.create(
                fontAppearance.font.fontFamily,
                fontAppearance.font.fontStyle
            )
            textAlign = fontAppearance.align
            isAntiAlias = true
        }

        val textLayoutAlign = when (fontAppearance.align) {
            Paint.Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            Paint.Align.LEFT -> Layout.Alignment.ALIGN_NORMAL
            Paint.Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }

        // now ask Android's StaticLayout to measure the needed height of the text soft-wrapped to
        // the width.
        val layout = if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            StaticLayout
                .Builder.obtain(spanned, 0, spanned.length, paint, width.dpAsPx(displayMetrics))
                .setAlignment(textLayoutAlign)
                .setLineSpacing(0f, 1.0f)
                // includePad ensures we don't clip off any of the ligatures that extend down past
                // the rule line.
                .setIncludePad(true)
                // Experiences app does not appear to wrap text on text blocks.  This seems particularly
                // important for short, tight blocks.
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build()
        } else {
            // On Android before 23 have to use the older interface for setting up a StaticLayout,
            // with which we sadly cannot configure it without hyphenation turned on, but this
            // only really effects edge cases anyway.
            StaticLayout(
                spanned,
                paint,
                width.dpAsPx(displayMetrics),
                textLayoutAlign,
                1.0f,
                0f,

                // includePad ensures we don't clip off any of the ligatures that extend down past
                // the rule line.
                true
            )
        }
        // log.v("Measured ${richText.lines().size} lines of text as needing ${layout.height} px")
        return (layout.height + layout.topPadding + layout.bottomPadding).pxAsDp(displayMetrics)
    }

    /**
     * Measure how much height a given bit of Unicode [text] will require if rendered as a barcode
     * in the given format.
     *
     * [type] specifies what format of barcode should be used, namely
     * [BarcodeViewModelInterface.BarcodeType]. Note that what length and sort of text is valid
     * depends on the type.
     *
     * Returns the height needed to accommodate the barcode, at the correct aspect, at the given
     * width, in dps.
     */
    fun measureHeightNeededForBarcode(
        text: String,
        type: BarcodeViewModelInterface.BarcodeType,
        width: Float
    ): Float {
        return barcodeRenderingService.measureHeightNeededForBarcode(
            text,
            when (type) {
                BarcodeViewModelInterface.BarcodeType.Aztec -> BarcodeRenderingService.Format.Aztec
                BarcodeViewModelInterface.BarcodeType.Code128 -> BarcodeRenderingService.Format.Code128
                BarcodeViewModelInterface.BarcodeType.PDF417 -> BarcodeRenderingService.Format.Pdf417
                BarcodeViewModelInterface.BarcodeType.QrCode -> BarcodeRenderingService.Format.QrCode
            },
            width
        )
    }
}
