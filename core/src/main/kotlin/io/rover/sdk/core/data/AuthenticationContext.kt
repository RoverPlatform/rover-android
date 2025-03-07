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

package io.rover.sdk.core.data

import java.util.Base64
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import org.json.JSONObject
import java.util.Date

/**
 * A version of Authentication Context that works with simply a standard SDK Key, acquired from
 * [Rover Profile/Account settings](https://app.rover.io/settings/overview), as "SDK Token" under
 * the "Account Tokens"->"Android" section.
 */
data class AuthenticationContext(
    /**
     * The Rover API key.
     */
    override val sdkToken: String?,
    var localStorage: LocalStorage
) : AuthenticationContextInterface {
    override val sdkAuthenticationEnabledDomains = mutableSetOf("api.rover.io")

    private val keyValueStorage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    override var sdkAuthenticationIdTokenRefreshCallback: () -> Unit = {}

    private var idTokenUpdates: MutableSharedFlow<String?> = MutableSharedFlow(replay = 0)

    private companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "authentication-context"
    }

    override suspend fun obtainSdkAuthenticationIdToken(checkValidity: Boolean): String? {
        // if we don't already have a token, then no refresh is necessary.
        val existingIDToken = keyValueStorage["sdkAuthenticationToken"] ?: return null
        if (!checkValidity) {
            return existingIDToken
        }

        // attempt to decode JWT token, checking for exp.
        val exp = getJwtExpiry(existingIDToken)

        if (exp == null) {
            log.w("Existing JWT token is invalid, cannot decode expiry. Keeping it.")
            return existingIDToken
        }

        if (exp < Date(Date().time + 60 * 1000)) {
            log.i("SDK Authentication ID Token expired or about to expire, and needs to be refreshed, holding request waiting for new token to be set.\n" +
                    "Please call Rover.shared.setSdkAuthorizationIdToken() with the new token within 10s.")
            return requestAndAwaitNewToken()
        } else {
            log.v("SDK Authentication ID Token is still valid. $exp")
        }

        return existingIDToken
    }

    override fun setSdkAuthenticationIdToken(token: String?) {
        keyValueStorage["sdkAuthenticationToken"] = token

        // launch coroutine on main to emit the new token to the flow.
        CoroutineScope(Dispatchers.Main).launch {
            idTokenUpdates.emit(token)
        }

        val expiry = getJwtExpiry(token ?: "")
        if (expiry == null) {
            log.w("Possibly invalid JWT ID token set.")
            return
        }
        val timeUntilExpiry = expiry.time.minus(Date().time) / 1000
        if (timeUntilExpiry < 0) {
            log.e("SDK Authentication ID Token has been set, but it is already expired. It expired ${-timeUntilExpiry}s ago.")
        } else {
            log.i("New SDK Authentication JWT ID Token set, with an expiry that is ${timeUntilExpiry}s in the future.")
        }
    }

    override fun clearSdkAuthenticationIdToken() {
        setSdkAuthenticationIdToken(null)
    }

    /**
     * Wait for a call to Rover.shared.setSDKAuthorizationIDToken()
     */
    private suspend fun requestAndAwaitNewToken(): String? {
        val flow = idTokenUpdates
            .onSubscription {
                // fire a request for a token refresh.
                withContext(Dispatchers.Main) {
                    sdkAuthenticationIdTokenRefreshCallback()
                }
            }
            .take(1)

        // wait for the setter to be called, with a timeout.
        return try {
            withTimeout(10000) {
                flow.single()
            }
        } catch (e: TimeoutCancellationException) {
            log.w("Rover.shared.setSdkAuthorizationIdToken() was not called within 10 seconds. Omitting token from request.")
            null
        }
    }

    override fun enableSdkAuthIdTokenRefreshForDomain(pattern: String) {
        sdkAuthenticationEnabledDomains.add(pattern)
    }
}

/**
 * Decode a JWT token and return the expiry time.
 *
 * Note this does not check the signature. The only task is to check if we should request a new
 * token.
 */
private fun getJwtExpiry(jwt: String): Date? {
    val parts = jwt.split(".")
    if (parts.size != 3) {
        return null
    }

    val decoded = try {
        Base64.getUrlDecoder().decode(parts[1])
    } catch (e: IllegalArgumentException) {
        return null
    }

    val obj = try {
        JSONObject(String(decoded))
    } catch (e: JSONException) {
        return null
    }

    if (obj.has("exp")) {
        return Date(obj.getLong("exp") * 1000)
    } else {
        return null
    }
}