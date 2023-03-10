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

@file:JvmName("Extensions")

package io.rover.sdk.experiences.classic

import android.util.DisplayMetrics
import io.rover.sdk.core.data.domain.Color
import io.rover.sdk.experiences.classic.concerns.MeasuredSize

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
