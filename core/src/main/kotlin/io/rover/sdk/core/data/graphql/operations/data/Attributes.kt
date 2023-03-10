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

package io.rover.sdk.core.data.graphql.operations.data

import io.rover.sdk.core.data.domain.Attributes
import io.rover.sdk.core.data.graphql.safeGetString
import org.json.JSONObject

fun JSONObject.toStringHash(): Map<String, String> {
    return this.keys().asSequence().associate { key ->
        Pair(key, this.safeGetString(key))
    }
}

fun JSONObject.toStringIntHash() = keys().asSequence().associateWith { key -> getInt(key) }

fun Attributes.encodeJson(): JSONObject {
    this.map { (_, value) ->
        @Suppress("UNCHECKED_CAST")
        val newValue = when (value) {
            is Map<*, *> -> (value as Attributes).encodeJson()
            is Collection<*> -> org.json.JSONArray(value)
            else -> value
        }
        newValue
    }
    return org.json.JSONObject(this)
}

fun JSONObject.toAttributesHash(): Attributes {
    return this.keys().asSequence().map { key ->
        val value = get(key)
        if (value is JSONObject) {
            Pair(key, value.toAttributesHash())
        } else {
            Pair(key, value)
        }
    }.associate { it }
}
