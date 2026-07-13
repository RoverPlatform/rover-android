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

package io.rover.sdk.experiences.appscreens.network

import android.content.Context
import android.net.Uri
import io.rover.sdk.core.data.AuthenticationContextInterface
import io.rover.sdk.core.data.OkHttpRequestAuthenticator
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.events.resolveUserIdentifier
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.DeviceIdentificationInterface
import io.rover.sdk.core.platform.roverUserAgent
import io.rover.sdk.experiences.appscreens.AppScreenDataScope
import io.rover.sdk.experiences.appscreens.AppScreensDecisions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * The scope-dependent HTTP client for the App Screen data (`.json`) channel.
 *
 * Unlike the document client this client carries NO disk cache (personalized data is
 * `private, no-store`). Its request shape branches on the effective [AppScreenDataScope]:
 *
 * - [AppScreenDataScope.PERSONALIZED]: appends the `deviceIdentifier` and (when resolvable)
 *   `userID` query parameters and attaches the fan `Authorization: Bearer <jwt>` header via the
 *   shared [OkHttpRequestAuthenticator] (which allowlist-gates on the SDK-auth-enabled domains and
 *   bridges the suspend token obtain with `runBlocking` — hence this whole call runs on
 *   [Dispatchers.IO], never the main thread).
 * - [AppScreenDataScope.PUBLIC]: a completely bare request — no identifiers, no auth, no account
 *   token.
 *
 * A single one-shot retry is performed inside [fetchScreenData]: if a PUBLIC request comes back
 * declaring PERSONALIZED scope (see [AppScreensDecisions.shouldRefetchWithIdentifiers]) the fetch
 * is repeated once as PERSONALIZED. It never loops.
 */
internal open class AppScreensDataClient(
    private val context: Context,
    private val authenticationContext: AuthenticationContextInterface,
    private val userInfo: UserInfoInterface,
    private val deviceIdentification: DeviceIdentificationInterface
) {
    private val userAgent: String by lazy {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.roverUserAgent
    }

    internal val client: OkHttpClient by lazy {
        OkHttpClient()
            .newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .build()
                )
            }
            .build()
    }

    /**
     * Fetch the `.json` data document for [screenUrl] under the given [scope], performing the
     * one-shot public→personalized retry internally. Throws [IOException] on a non-2xx response.
     */
    open suspend fun fetchScreenData(
        screenUrl: Uri,
        scope: AppScreenDataScope
    ): AppScreenDataEnvelope = withContext(Dispatchers.IO) {
        val dataUrl = AppScreensDecisions.dataUrl(screenUrl)

        val first = performFetch(dataUrl, scope)
        if (AppScreensDecisions.shouldRefetchWithIdentifiers(scope, first.responseScope)) {
            log.i("App Screen data scope flipped public→personalized — refetching with identifiers")
            return@withContext performFetch(dataUrl, AppScreenDataScope.PERSONALIZED)
        }
        first
    }

    private fun performFetch(dataUrl: Uri, scope: AppScreenDataScope): AppScreenDataEnvelope {
        val request = when (scope) {
            AppScreenDataScope.PUBLIC -> Request.Builder().url(dataUrl.toString()).get().build()
            AppScreenDataScope.PERSONALIZED -> {
                val decorated = dataUrl.buildUpon()
                    .appendQueryParameter("deviceIdentifier", deviceIdentification.installationIdentifier)
                    .apply {
                        userInfo.resolveUserIdentifier()?.let { appendQueryParameter("userID", it) }
                    }
                    .build()
                val bare = Request.Builder().url(decorated.toString()).get().build()
                OkHttpRequestAuthenticator.authenticate(authenticationContext, bare)
            }
        }

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("App Screen data fetch failed: HTTP ${response.code} for $dataUrl")
            }
            val rawJson = response.body?.string()
                ?: throw IOException("App Screen data response had no body for $dataUrl")

            val templateHash = try {
                JSONObject(rawJson).optString("templateHash", "").takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                log.w("App Screen data body was not parseable JSON while peeking templateHash: $e")
                null
            }
            val responseScope =
                AppScreenDataScope.fromHeader(response.header(AppScreenDataScope.HEADER_NAME))

            log.i(
                "App Screen data loaded: $dataUrl requestedScope=$scope responseScope=$responseScope " +
                    "templateHash=$templateHash"
            )

            AppScreenDataEnvelope(
                rawJson = rawJson,
                templateHash = templateHash,
                responseScope = responseScope
            )
        }
    }
}
