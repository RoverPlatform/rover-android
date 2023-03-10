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

package io.rover.sdk.experiences.data.http

import io.rover.sdk.experiences.rich.compose.data.ExperiencesHttpClient
import io.rover.sdk.experiences.rich.compose.data.JsonParser
import io.rover.sdk.experiences.rich.compose.model.values.CDNConfiguration
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * This API retrieves an experience from the given URL.
 *
 * Both classic and new experiences are supported.
 */
internal interface RoverExperiencesWebService {
    // so the shape of the returned body is not known until we read the value of the
    // `Rover-Experience-Version` header. Cannot use a custom converter factory, because
    // those cannot read headers. So will just have to return binary data and we'll
    // use moshi to decode it ourselves.

    @GET
    suspend fun fetchData(@Url url: String): Response<ResponseBody>

    /**
     * CDN configuration metadata is used to determine full URLs to resolve assets.
     * * (Not used by classic Experiences.)
     */
    @GET
    suspend fun getConfiguration(@Url url: String): CDNConfiguration

    companion object {
        fun make(httpClient: ExperiencesHttpClient): RoverExperiencesWebService {
            val retrofit = Retrofit
                .Builder()
                // This base URL is usually not used, but is required by Retrofit.
                .baseUrl("https://api.rover.io/")
                .addConverterFactory(MoshiConverterFactory.create(JsonParser.moshi))
                .client(
                    httpClient.client
                ).build()
            return retrofit.create(RoverExperiencesWebService::class.java)
        }
    }
}
