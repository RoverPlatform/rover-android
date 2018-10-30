package io.rover.core.data.graphql.operations.data

import io.rover.core.data.domain.AttributeValue
import io.rover.core.data.domain.Attributes
import io.rover.core.data.graphql.getIterable
import io.rover.core.data.graphql.safeGetString
import io.rover.core.platform.DateFormattingInterface
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
                AttributeValue.Scalar.URL(URI.create(this))
            } catch (e: IllegalArgumentException) {
                // not a valid URI, it's just a string!
                AttributeValue.Scalar.String(this)
            }
        }
        is Int -> {
            AttributeValue.Scalar.Integer(this)
        }
        is Double -> {
            AttributeValue.Scalar.Double(this)
        }
        is Boolean -> {
            AttributeValue.Scalar.Boolean(this)
        }
        is JSONObject -> {
            AttributeValue.Object(this.toFlatAttributesHash())
        }
        is JSONArray -> {
            AttributeValue.Array(this.getIterable<Any>().map { it.mapToScalarAttributeValueFromJsonPrimitive() })
        }
        else -> throw RuntimeException("Unsupported data type appeared in an attributes hash: ${javaClass.simpleName}")
    }

private fun Any.mapToScalarAttributeValueFromJsonPrimitive(): AttributeValue.Scalar {
    // org.json will return a best-effort coercion to either a system type
    // or one of its own internal types.
    return when (this) {
        is String -> {
            // now we have to try URL first before just returning a String type
            try {
                AttributeValue.Scalar.URL(URI.create(this))
            } catch (e: IllegalArgumentException) {
                // not a valid URI, it's just a string!
                AttributeValue.Scalar.String(this)
            }
        }
        is Int -> {
            AttributeValue.Scalar.Integer(this)
        }
        is Double -> {
            AttributeValue.Scalar.Double(this)
        }
        is Boolean -> {
            AttributeValue.Scalar.Boolean(this)
        }
        else -> throw RuntimeException("Unsupported data type appeared for scalar value in attributes hash: ${javaClass.simpleName}")
    }
}

/**
 * If we receive an arbitrary hash of values as JSON that does not map statically to
 * any sort of type, then [Attributes] is an appropriate choice.
 */
fun JSONObject.toAttributesHash(): Attributes {
    return this.keys().asSequence().map { key ->
        val uncoercedValue = this@toAttributesHash.get(key)
        Pair(key, uncoercedValue.mapToAttributeValueFromJsonPrimitive())
    }.associate { it }
}

/**
 * There may be nested arrays or JSON objects within Attributes, but they must only contain scalar
 * values.
 */
fun JSONObject.toFlatAttributesHash(): Map<String, AttributeValue.Scalar> {
    @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
    return toAttributesHash().filterValues { it is AttributeValue.Scalar } as Map<String, AttributeValue.Scalar>
}

fun JSONObject.toStringHash(): Map<String, String> {
    return this.keys().asSequence().associate { key ->
        Pair(key, this.safeGetString(key))
    }
}

/**
 * Encode this [AttributeValue] to values appropriate for use in a [JSONObject] or [JSONArray].
 * Return type is [Object] because this will return any of [Int], [String], [Double], [JSONObject],
 * [JSONArray] (again, the JSON library will treat these appropriately at render time).
 */
fun AttributeValue.encodeJson(dateFormatting: DateFormattingInterface): Any = when (this) {
    is AttributeValue.Scalar.Boolean -> this.value
    is AttributeValue.Scalar.Double -> this.value
    is AttributeValue.Object -> this.hash.encodeJson(dateFormatting)
    is AttributeValue.Scalar.String -> this.value
    is AttributeValue.Scalar.Integer -> this.value
    is AttributeValue.Scalar.URL -> this.value.toString()
    is AttributeValue.Scalar.Date -> dateFormatting.dateAsIso8601(this.date, false)
    is AttributeValue.Array -> JSONArray(this.values.map { it.encodeJson(dateFormatting) })
    else -> throw RuntimeException("illegal scalar subtype")
}

fun Attributes.encodeJson(dateFormatting: DateFormattingInterface): JSONObject {
    return JSONObject().apply {
        this@encodeJson.entries.forEach { (key, value) ->
            if (!key.matches(Regex("^[a-zA-Z_][a-zA-Z_0-9]*$"))) {
                throw RuntimeException("Invalid Rover Attribute Key: '$key'")
            }
            this.put(
                key,
                value.encodeJson(dateFormatting)
            )
        }
    }
}
