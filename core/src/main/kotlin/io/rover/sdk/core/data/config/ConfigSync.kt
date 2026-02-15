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
import io.rover.sdk.core.data.sync.SyncStandaloneParticipant
import io.rover.sdk.core.logging.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Syncs configuration from the Rover Engage API.
 *
 * Implements [SyncStandaloneParticipant] to participate in the SDK's sync coordinator system.
 * Fetches configuration from the backend and updates the [ConfigManager] on successful sync.
 */
internal class ConfigSync(
    private val engageApiService: EngageApiService,
    private val configManager: ConfigManager
) : SyncStandaloneParticipant {
    
    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(RoverConfig::class.java)

    /**
     * Syncs configuration from the backend.
     *
     * @return true if the sync was successful, false otherwise
     */
    override suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        try {
            log.d("Starting config sync")
            
            val response = engageApiService.getConfig()
            
            if (response.isSuccessful) {
                val responseBody = response.body()?.string()
                if (responseBody != null) {
                    log.d("Successfully received config response")
                    val config = adapter.fromJson(responseBody)
                        ?: throw IllegalArgumentException("Failed to parse config response")
                    
                    configManager.updateFromBackend(config)
                    log.d("Config sync completed successfully")
                    true
                } else {
                    log.w("Empty response body from config API")
                    false
                }
            } else {
                log.w("Config sync failed with HTTP ${response.code()}: ${response.message()}")
                false
            }
        } catch (e: Exception) {
            log.w("Config sync failed: ${e.message}")
            false
        }
    }
}
