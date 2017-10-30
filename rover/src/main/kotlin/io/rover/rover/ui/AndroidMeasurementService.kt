package io.rover.rover.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Html
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.util.DisplayMetrics
import io.rover.rover.ui.types.dpAsPx
import io.rover.rover.ui.types.pxAsDp
import io.rover.rover.ui.viewmodels.FontFace

class AndroidMeasurementService(
    private val displayMetrics: DisplayMetrics
): MeasurementService {
    override fun measureHeightNeededForRichText(
        richText: String,
        fontFace: FontFace,
        width: Float
    ): Float {
        val spanned = legacyModeHtmlParse(richText)

        val paint = TextPaint().apply {
            textSize = fontFace.fontSize.toFloat() * displayMetrics.scaledDensity
            typeface = Typeface.create(
                fontFace.fontFamily, fontFace.fontStyle
            )
            textAlign = fontFace.align
        }

        val textLayoutAlign = when(fontFace.align) {
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
            0f,
            0f,
            false
        )

        return layout.height.pxAsDp(displayMetrics)
    }

    private fun legacyModeHtmlParse(richText: String): Spanned {
        return if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Html.fromHtml(richText)
        } else {
            Html.fromHtml(richText, Html.FROM_HTML_MODE_LEGACY)
        }
    }
}
