package io.rover.experiences.ui.blocks.concerns.text

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.TypefaceSpan
import io.rover.core.logging.log

/**
 * A span that specifies a given fontFamily and fontStyle.  Unlike [TypefaceSpan] which only takes a
 * font family and otherwise maintains the style currently used in the drawing context (the [Paint],
 * this one instead allows you to specify it explicitly.  It will honour existing italic paint
 * style, but it always overrides the bold setting.
 */
class TypefaceAndExplicitBoldSpan(
    private val fontFamily: String,
    private val fontStyle: Int
) : TypefaceSpan(fontFamily) {
    private val typefaceCache: HashMap<Int, Typeface> = hashMapOf()

    override fun updateDrawState(paint: TextPaint) {
        applyWithStyle(paint)
    }

    override fun updateMeasureState(paint: TextPaint) {
        applyWithStyle(paint)
    }

    override fun getSpanTypeId(): Int {
        return 99
    }

    /**
     * Apply the appropriate typeface to the paint.
     *
     * Note: this is in a very hot path, so performance matters.
     */
    @SuppressLint("WrongConstant")
    private fun applyWithStyle(paint: Paint) {
        // @SuppressLint because we are using the proper style bits, but because we're passing it
        // through layers of our app just as Int, the static Android Linter is not able to determine
        // the pedigree of the Int values.

        // merge the current paint style italic mode with the requested style of this span.  We
        // don't want the existing BOLD bit to leak through though; we want exclusive control
        // over the bold bit with the provided fontStyle.
        val style = (paint.typeface.style and Typeface.ITALIC) or fontStyle

        // cache the typefaces.
        val tf = typefaceCache[style] ?: Typeface.create(fontFamily, style).apply { typefaceCache[style] = this }

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