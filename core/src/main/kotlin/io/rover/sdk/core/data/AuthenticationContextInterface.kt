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

/**
 * Implementers can furnish consumers with a Rover API account SDK authentication token, or a Bearer
 * OAuth token or null if authentication is not available.
 */
interface AuthenticationContextInterface {
    /**
     * The Rover API key (also known as "SDK Token") for the account.
     */
    val sdkToken: String?

    /**
     * Domains that SDK authentication is enabled for.
     */
    val sdkAuthenticationEnabledDomains: Set<String>

    /**
     * Set a JWT ID Token for the signed-in user, signed with RS256 or better.
     *
     * This securely attests to the user's identity to enable additional personalization features.
     *
     * Call this method when your user signs in with your account system, and whenever you do your
     * token-refresh cycle.
     */
    fun setSdkAuthenticationIdToken(token: String?)

    fun clearSdkAuthenticationIdToken()

    /**
     * A callback registered by the developer that is called when Rover determines it needs
     * a refreshed SDK Authentication ID Token.
     */
    var sdkAuthenticationIdTokenRefreshCallback: () -> Unit

    suspend fun obtainSdkAuthenticationIdToken(checkValidity: Boolean = true): String?

    /**
     * Enable SDK Authentication for the given domain pattern (* for globbing supported). Only
     * enable this for domains you trust!
     *
     * Outbound HTTP REST requests from a data source to this domain will have the SDK
     * authentication token added as a bearer Authorization header.
     */
    fun enableSdkAuthIdTokenRefreshForDomain(pattern: String)

    fun isAvailable(): Boolean {
        return sdkToken != null
    }
}
