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

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service for the Engage API config endpoint.
 * 
 * Note: This is a separate instance from the EngageApiService in the notifications module
 * to avoid exposing internal APIs across module boundaries.
 */
internal interface EngageApiService {
    @GET("config")
    suspend fun getConfig(): Response<ResponseBody>

    @GET("home")
    suspend fun getHomeView(
        @Query("userID") userID: String? = null,
        @Query("deviceIdentifier") deviceIdentifier: String? = null
    ): Response<ResponseBody>

    companion object {
        /**
         * Ensures a URL ends with a trailing slash, as required by Retrofit for base URLs.
         *
         * Retrofit requires base URLs to end with '/' when using relative paths in API endpoints
         * (such as @GET("config")). Without a trailing slash, Retrofit throws IllegalArgumentException
         * during initialization, causing app crashes.
         *
         * @param url The URL to normalize
         * @return The URL with a trailing slash, or empty string if input was empty
         */
        private fun ensureTrailingSlash(url: String): String {
            return when {
                url.isEmpty() -> url
                url.endsWith("/") -> url
                else -> "$url/"
            }
        }

        fun create(httpClient: EngageHttpClient, baseUrl: String): EngageApiService {
            val normalizedBaseUrl = ensureTrailingSlash(baseUrl)

            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(httpClient.client)
                .build()

            return retrofit.create(EngageApiService::class.java)
        }
    }
}
