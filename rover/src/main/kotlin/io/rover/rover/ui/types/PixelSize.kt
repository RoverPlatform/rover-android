package io.rover.rover.ui.types

import android.util.Size

/**
 * A simple width/height whole-number pixel size.
 *
 * Rather equivalent to Android 21's [Size], which is not available on Android 18 and does not
 * implement [Comparable] at any rate.
 */
data class PixelSize(val width: Int, val height: Int): Comparable<PixelSize> {
    override fun compareTo(other: PixelSize): Int {
        // what constitutes a smaller size? pixel count, naturally.
        val totalPixels = width * height
        val otherPixels = other.width * other.height
        return totalPixels - otherPixels
    }


}