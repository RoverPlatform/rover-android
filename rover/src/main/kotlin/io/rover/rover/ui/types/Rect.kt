package io.rover.rover.ui.types

/*
 * Copyright (C) 2017 Rover Inc.
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * A simple integer Rectangle.
 *
 * Somewhat equivalent to [android.graphics.Rect], but immutable and decoupled from the Android
 * API.
 */
data class Rect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun height(): Int = bottom - top

    fun width(): Int = right - left

    fun intersection(other: Rect): Rect? {
        // adapted from Android's implementation of Rect/RectF.
        return if (left < other.right && other.left < right
            && top < other.bottom && other.top < bottom) {
            Rect(
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

    fun offset(dx: Int, dy: Int): Rect {
        return Rect(
            left + dx,
            top + dy,
            right + dx,
            bottom + dy
        )
    }

    fun contains(other: Rect): Boolean {
        // adapted from Android's implementation of Rect/RectF.
        // check for empty first
        return (this.left < this.right && this.top < this.bottom
            // now check for containment
            && this.left <= other.left && this.top <= other.top
            && this.right >= other.right && this.bottom >= other.bottom)
    }
}

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
}

fun Rect.asAndroidRect(): android.graphics.Rect {
    return android.graphics.Rect(
        left, top, right, bottom
    )
}

fun RectF.asAndroidRectF(): android.graphics.RectF {
    return android.graphics.RectF(
        left, top, right, bottom
    )
}
