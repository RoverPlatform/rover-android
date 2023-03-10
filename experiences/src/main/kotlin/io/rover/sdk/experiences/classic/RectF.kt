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

package io.rover.sdk.experiences.classic

/**
 * A simple float Rectangle.
 *
 * Somewhat equivalent to [android.graphics.RectF], but immutable and decoupled from the Android
 * API.
 */
internal data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun height(): Float = bottom - top

    fun width(): Float = right - left

    fun intersection(other: RectF): RectF? {
        // adapted from Android's implementation of Rect/RectF.
        return if (left < other.right && other.left < right &&
            top < other.bottom && other.top < bottom
        ) {
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
        return (
            this.left < this.right && this.top < this.bottom &&
                // now check for containment
                this.left <= other.left && this.top <= other.top &&
                this.right >= other.right && this.bottom >= other.bottom
            )
    }

    fun asAndroidRectF(): android.graphics.RectF {
        return android.graphics.RectF(
            left,
            top,
            right,
            bottom
        )
    }
}
