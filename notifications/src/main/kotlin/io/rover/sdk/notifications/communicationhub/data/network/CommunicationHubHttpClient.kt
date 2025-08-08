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

package io.rover.sdk.notifications.communicationhub.data.network

import android.content.Context
import io.rover.sdk.core.platform.roverUserAgent
import okhttp3.OkHttpClient

internal class CommunicationHubHttpClient(
    private val context: Context,
    private val accountToken: String?
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
                val requestBuilder = chain.request().newBuilder().apply {
                    header("User-Agent", userAgent)
                    header("Content-Type", "application/json")
                    accountToken?.let { token ->
                        header("x-rover-account-token", token)
                    }
                }
                chain.proceed(requestBuilder.build())
            }.build()
    }
}