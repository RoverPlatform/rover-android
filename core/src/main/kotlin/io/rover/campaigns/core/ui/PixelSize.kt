package io.rover.campaigns.core.ui

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
