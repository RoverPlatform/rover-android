package io.rover.sdk.data.events

import android.content.Context
import android.content.SharedPreferences
import java.util.*

class InstallationIdentification(
    context: Context
) {
    private val prefs: SharedPreferences? = context.getSharedPreferences(BASE_CONTEXT_NAME, Context.MODE_PRIVATE)

    val installationIdentifier by lazy {
        // if persisted UUID not present then generate and persist a new one. Memoize it in memory.
        prefs?.getString(STORAGE_IDENTIFIER, null) ?: createNewUUID()
    }

    private fun createNewUUID(): String {
        return UUID.randomUUID().toString().apply { prefs?.edit()?.putString(STORAGE_IDENTIFIER, this)?.apply() }
    }

    companion object {
        private const val STORAGE_IDENTIFIER = "device-identification"
        private const val BASE_CONTEXT_NAME: String = "io.rover.sdk.local-storage"
    }
}