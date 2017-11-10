package io.rover.rover.platform

import android.content.Context
import android.content.Context.MODE_PRIVATE

/**
 * Very simple hash-like storage of keys and values.
 */
interface KeyValueStorage {
    fun get(key: String): String?
    fun set(key: String, value: String)
}

/**
 * Obtain a persistent key-value named persistent storage area.
 */
interface LocalStorage {
    fun getKeyValueStorageFor(namedContext: String): KeyValueStorage
}

/**
 * Implementation of [LocalStorage] using Android's SharedPreferences.
 */
class SharedPreferencesLocalStorage(
    val context: Context
) : LocalStorage {
    private val baseContextName = "io.rover.rover.platform.localstorage"

    val prefs = context.getSharedPreferences(baseContextName, MODE_PRIVATE)

    override fun getKeyValueStorageFor(namedContext: String): KeyValueStorage {
        return object : KeyValueStorage {
            override fun get(key: String): String? = prefs.getString("$namedContext.$key", null)

            override fun set(key: String, value: String) {
                prefs.edit().putString("$namedContext.$key", value).apply()
            }
        }
    }
}