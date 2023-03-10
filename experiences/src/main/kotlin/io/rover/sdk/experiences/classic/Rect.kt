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
internal data class Rect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun height(): Int = bottom - top

    fun width(): Int = right - left

    fun intersection(other: Rect): Rect? {
        // adapted from Android's implementation of Rect/RectF.
        return if (left < other.right && other.left < right &&
            top < other.bottom && other.top < bottom
        ) {
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
        return (
            this.left < this.right && this.top < this.bottom &&
                // now check for containment
                this.left <= other.left && this.top <= other.top &&
                this.right >= other.right && this.bottom >= other.bottom
            )
    }

    fun asAndroidRect(): android.graphics.Rect {
        return android.graphics.Rect(
            left,
            top,
            right,
            bottom
        )
    }
}
