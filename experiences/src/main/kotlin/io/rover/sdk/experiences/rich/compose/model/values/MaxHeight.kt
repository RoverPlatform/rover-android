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

package io.rover.sdk.experiences.rich.compose.model.values

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

internal sealed class MaxHeight {
    data class Finite(
        val value: Float
    ) : MaxHeight()

    @Suppress("CanSealedSubClassBeObject")
    class Infinite : MaxHeight()

    internal class MaxHeightJsonAdapter {
        @FromJson
        fun fromJson(reader: JsonReader): MaxHeight {
            return when (val jsonValue = reader.readJsonValue()) {
                is Double -> Finite(value = jsonValue.toFloat())
                is String -> Infinite()
                else -> throw JsonDataException()
            }
        }

        @ToJson
        @Suppress("UNUSED_PARAMETER")
        fun toJson(writer: JsonWriter, value: MaxHeight?) {
            throw UnsupportedOperationException()
        }
    }

    val floatValue: Float
        get() = when (this) {
            is MaxHeight.Infinite -> Float.POSITIVE_INFINITY
            is MaxHeight.Finite -> this.value
        }
}
