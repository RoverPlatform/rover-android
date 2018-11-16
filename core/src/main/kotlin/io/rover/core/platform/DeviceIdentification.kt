package io.rover.core.platform

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import io.rover.core.logging.log
import java.io.File
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
    private val storageContextIdentifier = "io.rover.rover.device-identification"
    private val identifierKey = "identifier"
    private val storage = localStorage.getKeyValueStorageFor(storageContextIdentifier)

    override val installationIdentifier by lazy {
        // Further reading: https://developer.android.com/training/articles/user-data-ids.html
        // if persisted UUID not present then generate and persist a new one. Memoize it in memory.
        (storage.get(identifierKey) ?: (
            (getAndClearSdk1IdentifierIfPresent() ?: UUID.randomUUID().toString()).apply {
                storage.set(identifierKey, this)
            }
        )).apply {
            log.v("Device Rover installation identifier: $this")
        }
    }

    // On many manufacturers' Android devices, the set device name manifests as the Bluetooth name,
    // but not as the device hostname.  So, we'll ignore the device hostname and use the Bluetooth
    // name, if available.
    override val deviceName: String? = Settings.Secure.getString(
        applicationContext.contentResolver, "bluetooth_name"
    )


    private fun getAndClearSdk1IdentifierIfPresent(): String? {
        val legacySharedPreferencesFile = "ROVER_SHARED_DEVICE"
        val legacySharedDevice = applicationContext.getSharedPreferences(legacySharedPreferencesFile, Context.MODE_PRIVATE)
        val legacyUdid = legacySharedDevice.getString("UDID", null)

        if(legacyUdid != null) {
            log.i("Migrated legacy Rover SDK 1.x installation identifier: $legacyUdid")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    log.v("Deleting legacy shared preferences file.")
                    applicationContext.deleteSharedPreferences("ROVER_SHARED_DEVICE")

                } catch (e: FileNotFoundException) {
                    log.w("Unable to delete legacy Rover shared preferences file: $e")
                }
            }
        }

        return legacyUdid
    }
}
