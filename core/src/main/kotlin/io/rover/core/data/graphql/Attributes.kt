package io.rover.core.data.graphql

import io.rover.core.data.domain.Attributes
import org.json.JSONObject

// TODO: move to another file, this shouldn't be Attributes.kt
fun JSONObject.toStringHash(): Map<String, String> {
    return this.keys().asSequence().associate { key ->
        Pair(key, this.safeGetString(key))
    }
}

fun Attributes.encodeJson(): JSONObject {
    // TODO: I still would need to teach this how to transform the date time values to our format, that is, just 8601.
    this.entries.map {  }
    return JSONObject(this)
}
