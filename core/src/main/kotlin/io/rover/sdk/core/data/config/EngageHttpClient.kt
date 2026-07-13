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

import android.content.Context
import io.rover.sdk.core.data.AuthenticationContextInterface
import io.rover.sdk.core.data.OkHttpRequestAuthenticator
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.events.resolveUserIdentifier
import io.rover.sdk.core.platform.DeviceIdentificationInterface
import io.rover.sdk.core.platform.roverUserAgent
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * HTTP client for Engage API config endpoint.
 *
 * Provides OkHttpClient configured with authentication headers for config requests.
 */
internal class EngageHttpClient(
    private val context: Context,
    private val authenticationContext: AuthenticationContextInterface,
    private val userInfo: UserInfoInterface,
    private val deviceIdentification: DeviceIdentificationInterface,
) {
    private val userAgent: String by lazy {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            0
        )
        packageInfo.roverUserAgent
    }

    val client: OkHttpClient by lazy {
        OkHttpClient()
            .newBuilder()
            .addInterceptor { chain ->
                var request = chain.request().newBuilder().apply {
                    header("User-Agent", userAgent)
                    header("Content-Type", "application/json")
                    authenticationContext.sdkToken?.let { token ->
                        header("x-rover-account-token", token)
                    }
                }.build()

                val engageUserId = userInfo.resolveUserIdentifier()
                request = decorateEngageRequest(request, engageUserId)
                if (engageUserId != null) {
                    request = OkHttpRequestAuthenticator.authenticate(authenticationContext, request)
                }

                chain.proceed(request)
            }.build()
    }

    private fun decorateEngageRequest(request: Request, userId: String?): Request {
        val urlBuilder = request.url.newBuilder()

        val deviceIdentifier = deviceIdentification.installationIdentifier
        if (deviceIdentifier.isNotBlank()) {
            urlBuilder.setQueryParameter("deviceIdentifier", deviceIdentifier)
        }

        userId?.let { userID ->
            urlBuilder.setQueryParameter("userID", userID)
        }

        return request.newBuilder()
            .url(urlBuilder.build())
            .build()
    }
}
