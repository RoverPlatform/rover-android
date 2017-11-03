package io.rover.rover.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.DisplayMetrics
import io.rover.rover.ui.types.Font
import io.rover.rover.ui.types.FontAppearance
import io.rover.rover.ui.types.dpAsPx
import io.rover.rover.ui.types.pxAsDp

class AndroidMeasurementService(
    private val displayMetrics: DisplayMetrics,
    private val richTextToSpannedTransformer: RichTextToSpannedTransformer
): MeasurementService {
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

        val textLayoutAlign = when(fontAppearance.align) {
            Paint.Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            Paint.Align.LEFT -> Layout.Alignment.ALIGN_NORMAL
            Paint.Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }

        // now ask Android's StaticLayout to measure the needed height of the text soft-wrapped to
        // the width.
        val layout = StaticLayout(
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

        // log.v("Measured ${richText.lines().size} lines of text as needing ${layout.height} px")

        return layout.height.pxAsDp(displayMetrics)
    }


}
