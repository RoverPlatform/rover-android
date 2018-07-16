package io.rover.core.data.graphql.operations.data

import io.rover.core.data.domain.AttributeValue
import io.rover.core.data.domain.Attributes
import io.rover.core.data.graphql.getIterable
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * [JSONObject.get] can return either a primitive Java type (String, Double, and so on) or
 * a [JSONObject]/[JSONArray] composite thereof.  This function will transform such a value
 * (sadly necessarily only typed as [Any]) into a Rover [AttributeValue] value in order
 * to avoid unnecessarily exposing `org.json` types to consumer code.
 */
private fun Any.mapToAttributeValueFromJsonPrimitive(): AttributeValue =
    // org.json will return a best-effort coercion to either a system type
    // or one of its own internal types.
    when (this) {
        is String -> {
            // now we have to try URL first before just returning a String type
            try {
                AttributeValue.URL(URI.create(this))
            } catch (e: IllegalArgumentException) {
                // not a valid URI, it's just a string!
                AttributeValue.String(this)
            }
        }
        is Int -> {
            AttributeValue.Integer(this)
        }
        is Double -> {
            AttributeValue.Double(this)
        }
        is JSONObject -> {
            AttributeValue.Hash(this.toFlatAttributesHash())
        }
        is JSONArray -> {
            AttributeValue.Array(this.getIterable<Any>().map { it.mapToAttributeValueFromJsonPrimitive() })
        }
        else -> throw RuntimeException("Unsupported data type appeared in an attributes hash: ${javaClass.simpleName}")
    }

/**
 * If we receive an arbitrary hash of values as JSON that does not map statically to
 * any sort of type, then [Attributes] is an appropriate choice.
 */
fun JSONObject.toFlatAttributesHash(): Attributes {
    return this.keys().asSequence().map { key ->
        val uncoercedValue = this@toFlatAttributesHash.get(key)
        Pair(key, uncoercedValue.mapToAttributeValueFromJsonPrimitive())
    }.associate { it }
}

/**
 * Encode this [AttributeValue] to values appropriate for use in a [JSONObject] or [JSONArray].
 * Return type is [Object] because this will return any of [Int], [String], [Double], [JSONObject],
 * [JSONArray] (again, the JSON library will treat these appropriately at render time).
 */
fun AttributeValue.encodeJson(): Any = when (this) {
    is AttributeValue.Boolean -> this.value
    is AttributeValue.Double -> this.value
    is AttributeValue.Hash -> this.hash.encodeJson()
    is AttributeValue.String -> this.value
    is AttributeValue.Integer -> this.value
    is AttributeValue.URL -> this.value.toString()
    is AttributeValue.Array -> JSONArray(this.values.map { it.encodeJson() })
}

fun Attributes.encodeJson(): JSONObject {
    return JSONObject().apply {
        this@encodeJson.entries.forEach { (key, value) ->
            this.put(
                key,
                value.encodeJson()
            )
        }
    }
}
