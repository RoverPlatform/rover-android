package io.rover.core.ui

/**
 * A simple float Rectangle.
 *
 * Somewhat equivalent to [android.graphics.RectF], but immutable and decoupled from the Android
 * API.
 */
data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun height(): Float = bottom - top

    fun width(): Float = right - left

    fun intersection(other: RectF): RectF? {
        // adapted from Android's implementation of Rect/RectF.
        return if (left < other.right && other.left < right
            && top < other.bottom && other.top < bottom) {
            RectF(
                if (left < other.left) {
                    other.left
                } else this.left,

                if (top < other.top) {
                    other.top
                } else this.top,

                if (right > other.right) {
                    other.right
                } else right,

                if (bottom > other.bottom) {
                    other.bottom
                } else bottom
            )
        } else null
    }

    fun offset(dx: Float, dy: Float): RectF {
        return RectF(
            left + dx,
            top + dy,
            right + dx,
            bottom + dy
        )
    }

    fun contains(other: RectF): Boolean {
        // adapted from Android's implementation of Rect/RectF.
        // check for empty first
        return (this.left < this.right && this.top < this.bottom
            // now check for containment
            && this.left <= other.left && this.top <= other.top
            && this.right >= other.right && this.bottom >= other.bottom)
    }

    fun asAndroidRectF(): android.graphics.RectF {
        return android.graphics.RectF(
            left, top, right, bottom
        )
    }
}
