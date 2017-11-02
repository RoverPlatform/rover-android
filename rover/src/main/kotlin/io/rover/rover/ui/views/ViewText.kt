package io.rover.rover.ui.views

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.Log
import android.util.LogPrinter
import android.view.Gravity
import android.widget.TextView
import io.rover.rover.core.logging.log
import io.rover.rover.platform.roverTextHtmlAsSpanned
import io.rover.rover.ui.viewmodels.TextBlockViewModelInterface

class ViewText(
    private val textView: TextView
): ViewTextInterface {
    init {
        textView.setLineSpacing(0f, 1.0f)



    }

    override var textViewModel: TextBlockViewModelInterface? = null
        set(viewModel) {
            if(viewModel != null) {
                // TODO: this is a lot of compute at bind-time.  But not sure where to put memoized android-specific stuff (the Spanned below) because the ViewModel is offlimits for Android stuff

                // TODO: these span transforms must be made available to the measurement service as well, or the measurements may be slightly out

                // http://flavienlaurent.com/blog/2014/01/31/spans/ handy blog about spanned

                val lp = LogPrinter(Log.VERBOSE, "ViewText")

                val spanned = viewModel.text.roverTextHtmlAsSpanned()
                // TextUtils.dumpSpans(spanned, lp, "  ")

                // we want the spanned bolds within the text to be relative to the base typeface'
                // for the entire text block.

                val styleSpans = spanned.getSpans(0, spanned.length, StyleSpan::class.java)

                val boldSpans = styleSpans.filter { it.style == Typeface.BOLD }
                log.v("There are ${boldSpans.size} bolds for '${viewModel.text}'")
                val boldFont = viewModel.boldRelativeToBlockWeight()
                boldSpans.forEach {
                    // replace the bold span with our own explicit typeface+style span.
                    val start = spanned.getSpanStart(it)
                    val end = spanned.getSpanEnd(it)

                    log.v("... bold span from $start to $end ('${spanned.substring(start, end)}')")

                    // spanned.removeSpan(it)
                    spanned.setSpan(
                        TypefaceAndExplicitBoldSpan(boldFont.fontFamily, boldFont.fontStyle),
                        start,
                        end,
                        0
                    )
                }

                textView.text = spanned

                textView.gravity = when(viewModel.fontFace.align) {
                    Paint.Align.RIGHT -> Gravity.END
                    Paint.Align.LEFT -> Gravity.START
                    Paint.Align.CENTER -> Gravity.CENTER_HORIZONTAL
                }

                textView.textSize = viewModel.fontFace.fontSize.toFloat()

                textView.setTextColor(viewModel.fontFace.color)

                textView.typeface = Typeface.create(
                    viewModel.fontFace.font.fontFamily, viewModel.fontFace.font.fontStyle
                )
            }
        }
}

/**
 * A span that specifies a given fontFamily and fontStyle.  Unlike [TypefaceSpan] which only takes a
 * font family and otherwise maintains the style currently used in the drawing context (the [Paint],
 * this one instead allows you to specify it explicitly.  It will honour existing italic paint
 * style, but it always overrides the bold setting.
 */
class TypefaceAndExplicitBoldSpan(
    private val fontFamily: String,
    private val fontStyle: Int
): TypefaceSpan(fontFamily) {
    override fun updateDrawState(paint: TextPaint) {
        applyWithStyle(paint, fontFamily)
    }

    override fun updateMeasureState(paint: TextPaint) {
        applyWithStyle(paint, fontFamily)
    }

    override fun getSpanTypeId(): Int {
        return 99
    }

    private fun applyWithStyle(paint: Paint, family: String) {
        // merge the current paint style italic mode with the requested style of this span.  We
        // don't want the existing BOLD bit to leak through though; we want exclusive control
        // over the build bit with the provided fontStyle.
        val style = (paint.typeface.style and Typeface.ITALIC) or fontStyle

        val tf = Typeface.create(family, style)
        // Typeface would have done best-effort to support the style we specified.  Check to see
        // if the Bold and Italic bits remained on, and if they did not, fall back to emulation.

        // if the given typeface does not come back with supporting the requested fontStyle (or
        // any style inherited from the paint already styled by other spans in effect), use
        // the fakeBold property or manually set a skew to emulate them.
        val notSupportedByTypeface = style and tf.style.inv()

        if (notSupportedByTypeface and Typeface.BOLD != 0) {
            log.w("Falling back to emulated bold effect for specified font family: '$fontFamily'")
            paint.isFakeBoldText = true
        }

        if (notSupportedByTypeface and Typeface.ITALIC != 0) {
            log.w("Falling back to emulated italicization for specified font family: '$fontFamily'")
            paint.textSkewX = -0.25f
        }

        paint.typeface = tf
    }
}
