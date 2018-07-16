package io.rover.experiences

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.DisplayMetrics
import io.rover.experiences.ui.blocks.concerns.text.RichTextToSpannedTransformer
import io.rover.experiences.ui.blocks.concerns.text.Font
import io.rover.experiences.ui.blocks.concerns.text.FontAppearance
import io.rover.core.ui.dpAsPx
import io.rover.core.ui.pxAsDp
import io.rover.experiences.ui.blocks.barcode.BarcodeViewModelInterface
import io.rover.shaded.zxing.com.google.zxing.BarcodeFormat
import io.rover.shaded.zxing.com.google.zxing.EncodeHintType
import io.rover.shaded.zxing.com.google.zxing.MultiFormatWriter

class AndroidMeasurementService(
    private val displayMetrics: DisplayMetrics,
    private val richTextToSpannedTransformer: RichTextToSpannedTransformer
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
        // sadly I think I just have to compute the entire barcode and measure the resulting bitmap.

        val renderedBitmap = MultiFormatWriter().encode(
            text,
            when (type) {
                BarcodeViewModelInterface.BarcodeType.PDF417 -> BarcodeFormat.PDF_417
                // this one will happily collapse to a minimum height of 1.  That ain't going to do
                BarcodeViewModelInterface.BarcodeType.Code128 -> BarcodeFormat.CODE_128
                BarcodeViewModelInterface.BarcodeType.Aztec -> BarcodeFormat.AZTEC
                BarcodeViewModelInterface.BarcodeType.QrCode -> BarcodeFormat.QR_CODE
            },
            // we want the minimum size.
            0,
            0,
            hashMapOf(
                // I furnish my own margin (see contributedPadding).  Some -- but not all --
                // of the barcode types look for this margin parameter and if they don't
                // find it include their own (pretty massive) margin.
                Pair(EncodeHintType.MARGIN, 0)
            )
        )

        val aspectRatio = if (type == BarcodeViewModelInterface.BarcodeType.Code128) {
            // Code 128 is our only 1 dimensional barcode type, but naturally it ultimately renders
            // into 2D space, so we want to define an appropriate hard-coded aspect ratio for it.
            2.26086956521739f
        } else renderedBitmap.width / renderedBitmap.height.toFloat()

        return width / aspectRatio
    }
}
