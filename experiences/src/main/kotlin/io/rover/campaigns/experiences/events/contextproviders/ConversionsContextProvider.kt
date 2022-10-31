package io.rover.campaigns.experiences.events.contextproviders

import io.rover.campaigns.core.data.domain.DeviceContext
import io.rover.campaigns.core.events.ContextProvider
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.platform.LocalStorage
import io.rover.campaigns.core.streams.subscribe
import io.rover.sdk.data.events.RoverEvent
import io.rover.sdk.services.EventEmitter
import org.json.JSONObject
import java.util.Date
import java.util.Locale

class ConversionsContextProvider(
    localStorage: LocalStorage
) : ContextProvider {

    fun startListening(emitter: EventEmitter) {
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

    private fun getConversion(event: RoverEvent): Pair<String, Long>? =
        when (event) {
            is RoverEvent.BlockTapped -> event.block.conversion?.let {
                Pair(
                    it.tag,
                    it.expires.seconds
                )
            }
            is RoverEvent.ScreenPresented -> event.screen.conversion?.let {
                Pair(
                    it.tag,
                    it.expires.seconds
                )
            }
            // NOTE: We always append the poll's option to the tag
            is RoverEvent.PollAnswered -> event.block.conversion?.let {
                val pollTag = event.option.text.replace(" ", "_").toLowerCase(Locale.ROOT)
                Pair("${it.tag}_${pollTag}", it.expires.seconds)
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
        return JSONObject(data.map {
            Pair(it.key, it.value.time)
        }.associate { it }).toString()
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
