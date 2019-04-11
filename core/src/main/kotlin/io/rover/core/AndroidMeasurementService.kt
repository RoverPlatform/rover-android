package io.rover.core

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.DisplayMetrics
import io.rover.core.ui.dpAsPx
import io.rover.core.ui.blocks.barcode.BarcodeViewModelInterface
import io.rover.core.ui.blocks.concerns.text.Font
import io.rover.core.ui.blocks.concerns.text.FontAppearance
import io.rover.core.ui.blocks.concerns.text.RichTextToSpannedTransformer
import io.rover.core.ui.pxAsDp
import io.rover.experiences.BarcodeRenderingServiceInterface
import io.rover.experiences.MeasurementService

class AndroidMeasurementService(
    private val displayMetrics: DisplayMetrics,
    private val richTextToSpannedTransformer: RichTextToSpannedTransformer,
    private val barcodeRenderingService: BarcodeRenderingServiceInterface
) : MeasurementService {
    @SuppressLint("NewApi")
    override fun measureHeightNeededForRichText(
        richText: String,
        fontAppearance: FontAppearance,
        boldFontAppearance: Font,
        width: Float
    ): Float {
        val spanned = richTextToSpannedTransformer.transform(richText, boldFontAppearance)

        val paint = TextPaint().apply {
            textSize = fontAppearance.fontSize.toFloat() * displayMetrics.scaledDensity
            typeface = Typeface.create(
                fontAppearance.font.fontFamily, fontAppearance.font.fontStyle
            )
            textAlign = fontAppearance.align
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

    override fun measureHeightNeededForBarcode(
        text: String,
        type: BarcodeViewModelInterface.BarcodeType,
        width: Float
    ): Float {
        return barcodeRenderingService.measureHeightNeededForBarcode(
            text,
            when(type) {
                BarcodeViewModelInterface.BarcodeType.Aztec -> BarcodeRenderingServiceInterface.Format.Aztec
                BarcodeViewModelInterface.BarcodeType.Code128 -> BarcodeRenderingServiceInterface.Format.Code128
                BarcodeViewModelInterface.BarcodeType.PDF417 -> BarcodeRenderingServiceInterface.Format.Pdf417
                BarcodeViewModelInterface.BarcodeType.QrCode -> BarcodeRenderingServiceInterface.Format.QrCode
            },
            width
        )
    }
}
