package io.rover.campaigns.core.platform

import android.content.Context
import android.os.Build
import android.provider.Settings
import io.rover.campaigns.core.logging.log
import java.io.FileNotFoundException
import java.util.UUID

interface DeviceIdentificationInterface {
    /**
     * A installation-specific, Rover generated UUID.
     */
    val installationIdentifier: String

    /**
     * User-set name for the device, if available.
     */
    val deviceName: String?
}

/**
 * Responsible for maintaining an installation identifier.
 */
class DeviceIdentification(
    private val applicationContext: Context,
    localStorage: LocalStorage
) : DeviceIdentificationInterface {
    private val identifierKey = "identifier"
    private val storage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    override val installationIdentifier by lazy {
        // Further reading: https://developer.android.com/training/articles/user-data-ids.html
        // if persisted UUID not present then generate and persist a new one. Memoize it in memory.
        (storage.get(identifierKey) ?: (
            (getAndClearSdk2IdentifierIfPresent() ?: UUID.randomUUID().toString()).apply {
                storage.set(identifierKey, this)
            }
        )).apply {
            log.v("Device Rover installation identifier: $this")
        }
    }

    // On many manufacturers' Android devices, the set device name manifests as the Bluetooth name,
    // but not as the device hostname.  So, we'll ignore the device hostname and use the Bluetooth
    // name, if available.
    override val deviceName: String?
        get() {
            return if (Build.VERSION.SDK_INT < 31) {
                return Settings.Secure.getString(
                    applicationContext.contentResolver,
                    "bluetooth_name"
                )
            } else {
                // TODO: fallback to getting hostname or similar.
                null
            }
        }

    private fun getAndClearSdk2IdentifierIfPresent(): String? {
        val legacySharedPreferences = applicationContext.getSharedPreferences(
            LEGACY_STORAGE_2X_SHARED_PREFERENCES,
            Context.MODE_PRIVATE
        )
        val legacyUdid = legacySharedPreferences.getString("identifier", null)

        if (legacyUdid != null) {
            log.i("Migrated legacy Rover SDK 2.x installation identifier: $legacyUdid")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    log.v("Deleting legacy shared preferences file.")
                    applicationContext.deleteSharedPreferences(LEGACY_STORAGE_2X_SHARED_PREFERENCES)
                } catch (e: FileNotFoundException) {
                    log.w("Unable to delete legacy Rover shared preferences file: $e")
                }
            }
        }
        return legacyUdid
    }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "device-identification"
        private const val LEGACY_STORAGE_2X_SHARED_PREFERENCES = "io.rover.core.platform.localstorage.io.rover.rover.device-identification"
    }
}
