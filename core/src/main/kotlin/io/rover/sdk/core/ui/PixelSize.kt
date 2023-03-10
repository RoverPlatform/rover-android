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

package io.rover.sdk.core.ui

import android.util.Size

/**
 * A simple width/height whole-number pixel size.
 *
 * Rather equivalent to Android 21's [Size], which is not available on Android 18 and does not
 * implement [Comparable] at any rate.
 */
data class PixelSize(val width: Int, val height: Int) : Comparable<PixelSize> {
    override fun compareTo(other: PixelSize): Int {
        // what constitutes a smaller size? pixel count, naturally.
        val totalPixels = width * height
        val otherPixels = other.width * other.height
        return totalPixels - otherPixels
    }

    fun times(factor: Int): PixelSize {
        return PixelSize(
            width * factor,
            height * factor
        )
    }

    operator fun times(factor: Float): PixelSize {
        return PixelSize(
            (width * factor).toInt(),
            (height * factor).toInt()
        )
    }

    operator fun div(divisor: Float): PixelSize {
        return PixelSize(
            (width / divisor).toInt(),
            (height / divisor).toInt()
        )
    }

    operator fun div(divisor: Int): PixelSize {
        return PixelSize(
            (width / divisor.toFloat()).toInt(),
            (height / divisor.toFloat()).toInt()
        )
    }

    operator fun plus(addend: Int): PixelSize {
        return PixelSize(
            width + addend,
            height + addend
        )
    }

    operator fun minus(subtrahend: Int): PixelSize {
        return PixelSize(
            width - subtrahend,
            height - subtrahend
        )
    }

    operator fun minus(subtrahend: PixelSize): PixelSize {
        return PixelSize(
            width - subtrahend.width,
            height - subtrahend.height
        )
    }
}
