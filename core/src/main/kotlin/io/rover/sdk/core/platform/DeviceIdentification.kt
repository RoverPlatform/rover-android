/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.core.platform

import android.content.Context
import android.os.Build
import io.rover.sdk.core.logging.log
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
    var deviceName: String?
}

/**
 * Responsible for maintaining an installation identifier.
 */
class DeviceIdentification(
    private val applicationContext: Context,
    localStorage: LocalStorage
) : DeviceIdentificationInterface {
    private val identifierKey = "identifier"
    private val deviceNameKey = "deviceName"
    private val storage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    override val installationIdentifier by lazy {
        // Further reading: https://developer.android.com/training/articles/user-data-ids.html
        // if persisted UUID not present then generate and persist a new one. Memoize it in memory.
        (
            storage[identifierKey] ?: (
                (getAndClearSdk2IdentifierIfPresent() ?: UUID.randomUUID().toString()).apply {
                    storage[identifierKey] = this
                }
                )
            ).apply {
            log.v("Device Rover installation identifier: $this")
        }
    }

    // On many manufacturers' Android devices, the set device name manifests as the Bluetooth name,
    // but not as the device hostname.  So, we'll ignore the device hostname and use the Bluetooth
    // name, if available.
    override var deviceName: String?
        get() {
            return (storage[deviceNameKey] ?: "Phone").apply {
                log.v("Device name: $this")
            }
        }

        set(value) {
            storage[deviceNameKey] = value
            log.v("Device name set to: $value")
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
