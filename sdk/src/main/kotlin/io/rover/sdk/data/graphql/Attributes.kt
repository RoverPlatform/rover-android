package io.rover.sdk.data.graphql

import io.rover.sdk.data.domain.Attributes
import org.json.JSONArray
import org.json.JSONObject

// TODO: move to another file, this shouldn't be Attributes.kt
fun JSONObject.toStringHash(): Map<String, String> {
    return this.keys().asSequence().associate { key ->
        Pair(key, this.safeGetString(key))
    }
}

fun Attributes.encodeJson(): JSONObject {
    this.map { (_, value) ->
        val newValue = when(value) {
            is Map<*, *> -> (value as Attributes).encodeJson()
            is Collection<*> -> JSONArray(value)
            else -> value
        }
        newValue
    }
    return JSONObject(this)
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
