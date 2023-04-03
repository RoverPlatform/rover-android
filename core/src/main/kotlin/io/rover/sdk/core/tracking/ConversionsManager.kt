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

package io.rover.sdk.core.tracking

import io.rover.sdk.core.data.graphql.getStringIterable
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

class ConversionsManager(
        localStorage: LocalStorage
) {
    private val store = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)
    var currentConversions: List<String> = try {
        when (val data = store[CONVERSIONS_KEY]) {
            null -> listOf()
            else -> JSONArray(data).getStringIterable().toList()
        }
    } catch (throwable: Throwable) {
        log.w("Corrupted conversion tags, ignoring and starting fresh. Cause ${throwable.message}")
        mutableListOf<String>()
    }
        private set(value) {
            field = value
            store[CONVERSIONS_KEY] = JSONArray(value).toString()
        }


    fun addConversion(tag: String) {
        val conversions = currentConversions.filter { it != tag }
        currentConversions = (listOf(tag) + conversions)
                .take(100)
    }

    fun addConversions(tags: List<String>) {
        val conversions = currentConversions.filter { it !in tags }
        currentConversions = (tags + conversions)
                .take(100)
    }

   internal fun migrateLegacyTags() {
        val legacyTags = try {
            when (val data = store[LEGACY_CONVERSIONS_KEY]) {
                null -> return
                else -> LegacyTagSet.decodeJson(data)
            }
        } catch (throwable: Throwable) {
            log.w("Corrupted legacy conversion tags, ignoring. Cause ${throwable.message}")
            store[LEGACY_CONVERSIONS_KEY] = null
            return
        }

        addConversions(legacyTags.sortedActiveValues())

        store[LEGACY_CONVERSIONS_KEY] = null
    }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "conversions"
        private const val LEGACY_CONVERSIONS_KEY = "conversions"
        private const val CONVERSIONS_KEY = "conversionsKey"
    }
}

private data class LegacyTagSet(
        private val data: Map<String, Date>
) {

    fun add(tag: String, expires: Date): LegacyTagSet {
        val mutableData = data.toMutableMap()
        mutableData[tag] = expires
        return LegacyTagSet(data = mutableData)
    }

    fun values(): List<String> = data.keys.toList()

    fun filterActiveTags() = LegacyTagSet(data = data.filter { Date().before(it.value) })

    fun sortedActiveValues(): List<String> {
        return data.filter { Date().before(it.value) }
                .toList()
                .sortedByDescending { it.second }
                .map { it.first }
    }

    fun encodeJson(): String {
        return JSONObject(
                data.map {
                    Pair(it.key, it.value.time)
                }.associate { it }
        ).toString()
    }

    companion object {
        fun decodeJson(input: String): LegacyTagSet {
            val json = JSONObject(input)
            val data = json.keys().asSequence().map {
                val value = json.get(it)
                val expires = if (value is Long) Date(value) else Date()
                Pair(it, expires)
            }.associate { it }

            return LegacyTagSet(data = data)
        }

        fun empty(): LegacyTagSet = LegacyTagSet(data = emptyMap())
    }
}
