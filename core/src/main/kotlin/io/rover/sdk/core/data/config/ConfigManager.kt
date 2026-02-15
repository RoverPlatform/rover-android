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

package io.rover.sdk.core.data.config

import com.squareup.moshi.Moshi
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager for SDK configuration.
 *
 * Manages the active configuration for the SDK, handling backend-provided configuration.
 * The manager persists configuration to SharedPreferences and publishes changes to the active
 * config via a StateFlow, making it suitable for reactive observation.
 */
class ConfigManager(
    private val localStorage: LocalStorage
) {
    private val storage = localStorage.getKeyValueStorageFor("config")
    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(RoverConfig::class.java)

    private val _config = MutableStateFlow(loadConfig())
    
    /**
     * The currently active configuration.
     *
     * Returns the backend configuration if available, otherwise returns a default configuration.
     */
    val config: StateFlow<RoverConfig> = _config.asStateFlow()

    companion object {
        private const val STORAGE_KEY = "io.rover.communicationHub.config"
    }

    /**
     * Loads configuration from storage.
     *
     * Returns the stored configuration if available and valid, otherwise returns a default configuration.
     * If the cached configuration fails to deserialize (e.g., due to schema changes), the cache is
     * cleared and a fresh fetch will be triggered by ConfigSync.
     */
    private fun loadConfig(): RoverConfig {
        val data = storage[STORAGE_KEY]
        if (data == null) {
            log.d("No stored config found, using default")
            return RoverConfig()
        }

        return try {
            val config = adapter.fromJson(data)
            if (config != null) {
                log.d("Loaded config from storage")
                config
            } else {
                log.w("Failed to decode config: null result, clearing cache")
                storage.unset(STORAGE_KEY)
                RoverConfig()
            }
        } catch (e: Exception) {
            log.w("Failed to decode config: ${e.message}, clearing cache")
            storage.unset(STORAGE_KEY)
            RoverConfig()
        }
    }

    /**
     * Updates the configuration from the backend.
     *
     * Saves the provided configuration and activates it immediately.
     *
     * @param newConfig The configuration received from the backend.
     */
    fun updateFromBackend(newConfig: RoverConfig) {
        try {
            val json = adapter.toJson(newConfig)
            storage[STORAGE_KEY] = json
            _config.value = newConfig
            log.d("Backend config saved and activated")
        } catch (e: Exception) {
            log.w("Failed to encode config: ${e.message}")
        }
    }
}
