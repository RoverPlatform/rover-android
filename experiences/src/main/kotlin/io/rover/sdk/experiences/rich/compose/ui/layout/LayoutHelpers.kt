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

package io.rover.sdk.experiences.rich.compose.ui.layout

import androidx.compose.ui.unit.Constraints

/**
 * Perform some operation on an Int value unless it is a [Constraints.Infinity] value, in which case
 * pass the infinity through.
 *
 * This is often useful when processing handling maxWidth/maxHeight values arriving via
 * [Constraints] in measurement policies.
 */
internal inline fun Int.unlessInfinity(map: (Int) -> Int): Int =
    if (this == Constraints.Infinity) this else map(this)

/**
 * If an Int value is an infinity, replace it with the value yielded by [fallback].
 */
internal inline fun Int.ifInfinity(fallback: (Int) -> Int): Int =
    if (this == Constraints.Infinity) fallback(this) else this

internal inline fun Int.ifZero(fallback: () -> Int): Int =
    if (this == 0) fallback() else this

/**
 * Perform some operation on a Float value unless it is a [Float.isInfinite] value, in which case
 * pass the infinity through.
 */
internal inline fun Float.unlessInfinity(map: (Float) -> Float): Float =
    if (this.isInfinite()) this else map(this)

/**
 * If a Float value is an infinity, replace it with the value yielded by [fallback].
 */
internal inline fun Float.ifInfinity(fallback: (Float) -> Float): Float =
    if (this.isInfinite()) fallback(this) else this
