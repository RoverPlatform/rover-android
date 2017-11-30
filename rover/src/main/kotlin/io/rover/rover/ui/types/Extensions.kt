package io.rover.rover.ui.types

import android.util.DisplayMetrics

/**
 * Convert display-independent DP metrics to an appropriate value for this display.
 *
 * See "Converting DP Units to Pixel Units" on
 * https://developer.android.com/guide/practices/screens_support.html
 */
fun Float.dpAsPx(displayMetrics: DisplayMetrics): Int {
    val scale = displayMetrics.density
    return (this * scale + 0.5f).toInt()
}

/**
 * Convert display-independent DP metrics to an appropriate value for this display.
 *
 * See [Converting DP Units to Pixel Units](https://developer.android.com/guide/practices/screens_support.html)
 */
fun Int.dpAsPx(displayMetrics: DisplayMetrics): Int {
    return dpAsPx(displayMetrics.density)
}

fun Int.dpAsPx(displayDensity: Float): Int {
    // TODO change to: TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, metrics)
    val scale = displayDensity
    return (this * scale + 0.5f).toInt()
}

fun Int.pxAsDp(displayMetrics: DisplayMetrics): Float {
    return pxAsDp(displayMetrics.density)
}

fun Int.pxAsDp(displayDensity: Float): Float {
    val scale = displayDensity
    return this / scale
}

/**
 * Convert a [RectF] of display-independent DP metrics to an appropriate value for this display.
 *
 * See "Converting DP Units to Pixel Units" on
 * https://developer.android.com/guide/practices/screens_support.html
 */
fun RectF.dpAsPx(displayMetrics: DisplayMetrics): Rect {
    return Rect(
        left.dpAsPx(displayMetrics),
        top.dpAsPx(displayMetrics),
        right.dpAsPx(displayMetrics),
        bottom.dpAsPx(displayMetrics)
    )
}