@file:JvmName("Extensions")

package io.rover.campaigns.experiences.ui

import android.util.DisplayMetrics
import io.rover.campaigns.experiences.ui.concerns.MeasuredSize
import io.rover.campaigns.experiences.data.domain.Color

/**
 * Convert display-independent DP metrics to an appropriate value for this display.
 *
 * See "Converting DP Units to Pixel Units" on
 * https://developer.android.com/guide/practices/screens_support.html
 */
internal fun Float.dpAsPx(displayMetrics: DisplayMetrics): Int {
    return this.dpAsPx(displayMetrics.density)
}

internal fun Float.dpAsPx(displayDensity: Float): Int {
    // TODO change to: TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, metrics)
    return (this * displayDensity + 0.5f).toInt()
}

/**
 * Convert display-independent DP metrics to an appropriate value for this display.
 *
 * See [Converting DP Units to Pixel Units](https://developer.android.com/guide/practices/screens_support.html)
 */
internal fun Int.dpAsPx(displayMetrics: DisplayMetrics): Int {
    return dpAsPx(displayMetrics.density)
}

internal fun Int.dpAsPx(displayDensity: Float): Int {
    // TODO change to: TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, metrics)
    return (this * displayDensity + 0.5f).toInt()
}

internal fun Int.pxAsDp(displayMetrics: DisplayMetrics): Float {
    return pxAsDp(displayMetrics.density)
}

internal fun Int.pxAsDp(displayDensity: Float): Float {
    return this / displayDensity
}

/**
 * Convert a [RectF] of display-independent DP metrics to an appropriate value for this display.
 *
 * See "Converting DP Units to Pixel Units" on
 * https://developer.android.com/guide/practices/screens_support.html
 */
internal fun RectF.dpAsPx(displayMetrics: DisplayMetrics): Rect {
    return Rect(
        left.dpAsPx(displayMetrics),
        top.dpAsPx(displayMetrics),
        right.dpAsPx(displayMetrics),
        bottom.dpAsPx(displayMetrics)
    )
}

internal fun Color.asAndroidColor(): Int {
    return (alpha * 255).toInt() shl 24 or (red shl 16) or (green shl 8) or blue
}

internal fun RectF.toMeasuredSize(density: Float): MeasuredSize {
    return MeasuredSize(
        width(),
        height(),
        density
    )
}
