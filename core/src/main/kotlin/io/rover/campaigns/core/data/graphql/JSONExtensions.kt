@file:JvmName("JsonExtensions")

package io.rover.campaigns.core.data.graphql

import android.os.Build
import io.rover.campaigns.core.platform.DateFormattingInterface
import io.rover.campaigns.core.platform.whenNotNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException
import java.text.ParseException
import java.util.Date
import kotlin.reflect.KProperty1

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

/**
 * The stock [JSONObject.optString] method has a nasty known bug for which the behaviour is kept
 * for backwards bug compatibility: if a `null` literal appears as the value for the string, you'll
 * get the string "null" back instead of a null value.
 *
 * This version of the method solves that problem.
 *
 * See [Android Bug #36924550](https://issuetracker.google.com/issues/36924550).
 */
fun JSONObject.safeOptString(name: String): String? {
    return if (isNull(name)) null else optString(name, null)
}

/**
 * The stock [JSONObject.getString] method has a nasty known bug for which the behaviour is kept
 * for backwards bug compatibility: if a `null` literal appears as the value for the string, you'll
 * get the string "null" back instead of raising an exception.
 *
 * This version of the method solves that problem.
 *
 * See [Android Bug #36924550](https://issuetracker.google.com/issues/36924550).
 */
fun JSONObject.safeGetString(name: String): String {
    if (isNull(name)) {
        throw JSONException("Field '$name' is null instead of string")
    }
    return getString(name)
}

fun JSONObject.safeGetUri(name: String): URI {
    val field = this.safeGetString(name)
//    if(field == "") {
//        throw JSONException("Invalid URI.  Must not be an empty string.")
//    }
    return try {
        URI(field)
    } catch (e: URISyntaxException) {
        if (Build.VERSION.SDK_INT >= 27) {
            throw JSONException("URI syntax problem", e)
        } else {
            throw JSONException("URI syntax problem: ${e.message}")
        }
    }
}

/**
 * The stock [JSONObject.optBoolean] method cannot tell you if the value was unset or not present.
 */
fun JSONObject.safeOptBoolean(name: String): Boolean? {
    return if (isNull(name) || !this.has(name)) null else optBoolean(name)
}

fun JSONObject.safeOptInt(name: String): Int? {
    return if (isNull(name) || !this.has(name)) null else optInt(name)
}

fun JSONObject.getDate(name: String, dateFormatting: DateFormattingInterface, localTime: Boolean = false): Date {
    try {
        return dateFormatting.iso8601AsDate(getString(name), localTime)
    } catch (e: ParseException) {
        if (Build.VERSION.SDK_INT >= 27) {
            throw JSONException("Unable to parse date.", e)
        } else {
            throw JSONException("Unable to parse date, because: ${e.message}")
        }
    }
}

fun JSONObject.safeOptDate(name: String, dateFormatting: DateFormattingInterface, localTime: Boolean = false): Date? {
    try {
        return if (isNull(name) || !this.has(name)) null else optString(name, null).whenNotNull { dateFormatting.iso8601AsDate(it, localTime) }
    } catch (e: ParseException) {
        if (Build.VERSION.SDK_INT >= 27) {
            throw JSONException("Unable to parse date.", e)
        } else {
            throw JSONException("Unable to parse date, because: ${e.message}")
        }
    }
}

fun <T, R> JSONObject.putProp(obj: T, prop: KProperty1<T, R>, transform: ((R) -> Any?)? = null) {
    put(
        prop.name,
        if (transform != null) transform(prop.get(obj)) else prop.get(obj)
    )
}

fun <T, R> JSONObject.putProp(obj: T, prop: KProperty1<T, R>, name: String, transform: ((R) -> Any?)? = null) {
    put(
        name,
        if (transform != null) transform(prop.get(obj)) else prop.get(obj)
    )
}

/**
 * Get an [Iterable] over a [JSONArray], assuming/coercing all within to be strings.
 */
fun JSONArray.getStringIterable(): Iterable<String> = getIterable()

/**
 * Get an [Iterable] over a [JSONArray], assuming/coercing all within to be [JSONObject]s.
 */
fun JSONArray.getObjectIterable(): Iterable<JSONObject> = getIterable()

@Suppress("UNCHECKED_CAST")
fun <T> JSONArray.getIterable(): Iterable<T> {
    return object : Iterable<T> {
        private var counter = 0
        override fun iterator(): Iterator<T> {
            return object : Iterator<T> {
                override fun hasNext(): Boolean = counter < this@getIterable.length()

                override fun next(): T {
                    if (counter >= this@getIterable.length()) {
                        throw Exception("Iterator ran past the end!")
                    }
                    val jsonObject = this@getIterable.get(counter)
                    counter++
                    return jsonObject as T
                }
            }
        }
    }
}