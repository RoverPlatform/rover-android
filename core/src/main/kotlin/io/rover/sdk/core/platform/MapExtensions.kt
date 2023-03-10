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

package io.rover.sdk.core.platform

/**
 * Merge two hashes together. In the event of the same key existing, [transform] is invoked to
 * merge the two values.
 */
fun <TKey, TValue> Map<TKey, TValue>.merge(other: Map<TKey, TValue>, transform: (TValue, TValue) -> TValue): Map<TKey, TValue> {
    val keysSet = this.keys + other.keys

    return keysSet.map { key ->
        val left = this[key]
        val right = other[key]

        val value = when {
            left != null && right != null -> transform(left, right)
            left != null -> left
            right != null -> right
            else -> throw RuntimeException("Value for $key unexpectedly disappeared from both hashes being merged by Map.merge().")
        }

        Pair(key, value)
    }.associate { it }
}
