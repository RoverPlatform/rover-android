package io.rover.campaigns.core.data.graphql.operations.data

import io.rover.campaigns.core.data.domain.Attributes
import io.rover.campaigns.core.data.graphql.safeGetString
import org.json.JSONObject

fun JSONObject.toStringHash(): Map<String, String> {
    return this.keys().asSequence().associate { key ->
        Pair(key, this.safeGetString(key))
    }
}

fun Attributes.encodeJson(): JSONObject {
    this.map { (_, value) ->
        @Suppress("UNCHECKED_CAST") val newValue = when (value) {
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
