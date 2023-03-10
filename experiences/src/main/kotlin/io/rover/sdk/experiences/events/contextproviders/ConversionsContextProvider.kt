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

package io.rover.sdk.experiences.events.contextproviders

import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.experiences.data.events.MiniAnalyticsEvent
import io.rover.sdk.experiences.services.ClassicEventEmitter
import org.json.JSONObject
import java.util.Date
import java.util.Locale

internal class ConversionsContextProvider(
    localStorage: LocalStorage
) : ContextProvider {

    fun startListening(emitter: ClassicEventEmitter) {
        emitter.trackedEvents.subscribe { event ->
            getConversion(event)?.let { (tag, expires) ->
                currentConversions =
                    currentConversions.add(tag, Date(Date().time + expires * 1000))
            }
        }
    }

    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(conversions = currentConversions.values())
    }

    private val store = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)
    private var currentConversions: TagSet = try {
        when (val data = store[CONVERSIONS_KEY]) {
            null -> TagSet.empty()
            else -> TagSet.decodeJson(data)
        }
    } catch (throwable: Throwable) {
        log.w("Corrupted conversion tags, ignoring and starting fresh. Cause ${throwable.message}")
        TagSet.empty()
    }
        get() {
            return field.filterActiveTags()
        }
        set(value) {
            field = value.filterActiveTags()
            store[CONVERSIONS_KEY] = field.encodeJson()
        }

    private fun getConversion(event: MiniAnalyticsEvent): Pair<String, Long>? =
        when (event) {
            is MiniAnalyticsEvent.BlockTapped -> event.block.conversion?.let {
                Pair(
                    it.tag,
                    it.expires.seconds
                )
            }
            is MiniAnalyticsEvent.ScreenPresented -> event.screen.conversion?.let {
                Pair(
                    it.tag,
                    it.expires.seconds
                )
            }
            // NOTE: We always append the poll's option to the tag
            is MiniAnalyticsEvent.PollAnswered -> event.block.conversion?.let {
                val pollTag = event.option.text.replace(" ", "_").toLowerCase(Locale.ROOT)
                Pair("${it.tag}_$pollTag", it.expires.seconds)
            }
            else -> null
        }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "conversions"
        private const val CONVERSIONS_KEY = "conversions"
    }
}

private data class TagSet(
    private val data: Map<String, Date>
) {

    fun add(tag: String, expires: Date): TagSet {
        val mutableData = data.toMutableMap()
        mutableData[tag] = expires
        return TagSet(data = mutableData)
    }

    fun values(): List<String> = data.keys.toList()

    fun filterActiveTags() = TagSet(data = data.filter { Date().before(it.value) })

    fun encodeJson(): String {
        return JSONObject(
            data.map {
                Pair(it.key, it.value.time)
            }.associate { it }
        ).toString()
    }

    companion object {
        fun decodeJson(input: String): TagSet {
            val json = JSONObject(input)
            val data = json.keys().asSequence().map {
                val value = json.get(it)
                val expires = if (value is Long) Date(value) else Date()
                Pair(it, expires)
            }.associate { it }

            return TagSet(data = data)
        }

        fun empty(): TagSet = TagSet(data = emptyMap())
    }
}
