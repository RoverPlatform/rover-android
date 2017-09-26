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
): LocalStorage {
    private val baseContextName = "io.rover.rover.platform.localstorage"

    override fun getKeyValueStorageFor(namedContext: String): KeyValueStorage {
        val prefs = context.getSharedPreferences("$baseContextName.$namedContext", MODE_PRIVATE)
        return object : KeyValueStorage {
            override fun get(key: String): String? = prefs.getString(key, null)

            override fun set(key: String, value: String) {
                prefs.edit().putString(key, value).apply()
            }
        }
    }
}