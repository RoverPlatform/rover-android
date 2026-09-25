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
import io.rover.sdk.core.benchsupport.BenchSupportOverrideStore
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager for the Rover Hub's home view experience URL.
 *
 * Fetches the home view URL from the `/home` endpoint, caches the response in
 * SharedPreferences, and publishes changes for reactive observation. The cached value is
 * loaded during initialization so the most recent URL is available immediately.
 *
 * **Fetch strategy:** Requests include the shared Engage identity query parameters
 * from [EngageHttpClient]. On success, the response is persisted and published; on
 * failure, the cached value is preserved.
 *
 * @see ConfigManager for the configuration caching pattern.
 */
internal class HomeViewManager(
    private val engageApiService: EngageApiService,
    private val localStorage: LocalStorage,
) {
    private val storage = localStorage.getKeyValueStorageFor("home-view")
    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(HomeViewResponse::class.java)

    /**
     * Guards the backend value and the publication derived from it. See [publishExperienceURL].
     */
    private val publicationLock = Any()

    /**
     * The most recent fetched (or cached) URL, before the Bench override. This is what gets
     * cached; the override is never written to storage.
     *
     * Only read or written while holding [publicationLock], after construction.
     */
    private var backendExperienceURL = loadCachedResponse()?.experienceURL

    private val _experienceURL = MutableStateFlow(
        BenchSupportOverrideStore.applyToHomeExperienceURL(backendExperienceURL)
    )

    /**
     * The currently available home view experience URL.
     *
     * Returns the most recent successful fetch result or the cached value loaded from
     * SharedPreferences during initialization, with any Bench override layered on top. A
     * `null` value indicates no home view.
     */
    val experienceURL: StateFlow<String?> = _experienceURL.asStateFlow()

    init {
        // The override is a layer over whatever the backend last gave us, so a change to it
        // re-publishes against the current fetched URL rather than replacing it.
        BenchSupportOverrideStore.addChangeListener { publishExperienceURL() }
    }

    companion object {
        internal const val STORAGE_KEY = "io.rover.homeView.response"
    }

    /**
     * Fetches the home view URL from the `/home` endpoint.
     *
     * On success, the response is cached and [experienceURL] is updated if the value
     * changed. On failure, the cached value is preserved.
     */
    suspend fun fetch() {
        try {
            val response = engageApiService.getHomeView()

            if (response.isSuccessful) {
                val responseBody = response.body()?.string()
                if (responseBody != null) {
                    log.d("Successfully received home view response")
                    val homeViewResponse = adapter.fromJson(responseBody)
                    
                    if (homeViewResponse != null) {
                        synchronized(publicationLock) {
                            backendExperienceURL = homeViewResponse.experienceURL
                            publishExperienceURL()
                        }
                        saveToCache(homeViewResponse)
                        log.d("Home view URL fetched successfully")
                    } else {
                        log.w("Failed to decode home view response")
                    }
                } else {
                    log.w("Empty response body from home view API")
                }
            } else {
                log.e("Failed to fetch home view URL: HTTP ${response.code()}: ${response.message()}")
            }
        } catch (e: Exception) {
            // Silent failure - preserve cached URL
            log.e("Failed to fetch home view URL: ${e.message}")
        }
    }

    /**
     * Publishes the effective URL: the last fetched value with the current Bench override
     * layered on top.
     *
     * Locked for the same reason as [ConfigManager.config]'s publication: [fetch] completes on
     * the IO dispatcher while a Bench override change re-publishes on whichever thread set it,
     * and unserialized read-compute-assign steps let an older snapshot land last.
     */
    private fun publishExperienceURL() = synchronized(publicationLock) {
        _experienceURL.value = BenchSupportOverrideStore.applyToHomeExperienceURL(backendExperienceURL)
    }

    private fun loadCachedResponse(): HomeViewResponse? {
        val data = storage[STORAGE_KEY] ?: return null
        return try {
            adapter.fromJson(data)
        } catch (e: Exception) {
            log.w("Failed to decode cached home view response: ${e.message}")
            null
        }
    }

    private fun saveToCache(response: HomeViewResponse) {
        try {
            val json = adapter.toJson(response)
            storage[STORAGE_KEY] = json
        } catch (e: Exception) {
            log.w("Failed to encode home view response: ${e.message}")
        }
    }
}
