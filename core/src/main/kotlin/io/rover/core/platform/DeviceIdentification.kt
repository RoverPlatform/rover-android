package io.rover.core.platform

import android.content.Context
import android.provider.Settings
import io.rover.core.logging.log
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
            UUID.randomUUID().toString().apply {
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
}
