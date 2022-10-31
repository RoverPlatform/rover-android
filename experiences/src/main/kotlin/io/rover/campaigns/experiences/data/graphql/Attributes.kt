package io.rover.campaigns.experiences.data.graphql

import io.rover.campaigns.experiences.data.domain.Attributes
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.toStringHash(): Map<String, String> {
    return this.keys().asSequence().associate { key ->
        Pair(key, this.safeGetString(key))
    }
}

internal fun JSONObject.toStringIntHash() = keys().asSequence().associateWith { key -> getInt(key) }

internal fun Attributes.encodeJson(): JSONObject {
    this.map { (_, value) ->
        @Suppress("UNCHECKED_CAST") val newValue = when (value) {
            is Map<*, *> -> (value as Attributes).encodeJson()
            is Collection<*> -> JSONArray(value)
            else -> value
        }
        newValue
    }
    return JSONObject(this)
}

internal fun JSONObject.toAttributesHash(): Attributes {
    return this.keys().asSequence().map { key ->
        val value = get(key)
        if (value is JSONObject) {
            Pair(key, value.toAttributesHash())
        } else {
            Pair(key, value)
        }
    }.associate { it }
}
