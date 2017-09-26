package io.rover.rover.platform

import java.util.*

interface DeviceIdentificationInterface {
    val installationIdentifier: String
}

/**
 * Responsible for maintaining an installation identifier.
 */
class DeviceIdentification(
    localStorage: LocalStorage
): DeviceIdentificationInterface {
    private val storageContextIdentifier = "io.rover.rover.device-identification"
    private val identifierKey = "identifier"
    private val storage = localStorage.getKeyValueStorageFor(storageContextIdentifier)

    override val installationIdentifier by lazy {
        // Further reading: https://developer.android.com/training/articles/user-data-ids.html

        // if persisted UUID not present then generate and persist a new one. Memoize it in memory.
        storage.get(identifierKey) ?: (
            UUID.randomUUID().toString().apply {
                storage.set(identifierKey, this)
            }
        )
    }
}
