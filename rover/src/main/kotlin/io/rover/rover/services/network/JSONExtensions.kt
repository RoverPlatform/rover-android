package io.rover.rover.services.network

import org.json.JSONObject

/**
 * The standard [JSONObject.optInt] method does not support optional null values;
 * instead you must give a default int value.
 *
 * This method returns an optional Kotlin boxed [Int] value.
 */
fun JSONObject.optIntOrNull(name: String): Int? {
    val value = opt(name)
    return when (value) {
        is Int -> value
        is Number -> value.toInt()
        is String -> {
            try {
                java.lang.Double.parseDouble(value).toInt()
            } catch (ignored: NumberFormatException) {
                null
            }
        }
        else -> null
    }
}
