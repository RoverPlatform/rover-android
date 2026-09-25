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

package io.rover.sdk.core.routing

import android.content.Intent
import io.rover.sdk.core.logging.log
import java.net.URI

class RouterService() : Router {
    private val registeredRoutes: MutableSet<Route> = mutableSetOf()

    override fun route(uri: URI?, inbound: Boolean): Intent? {
        // First match wins: some routes resolve with side effects (queueing navigation on a
        // coordinator), so consulting later ones would fire those twice.
        for (route in registeredRoutes) {
            val resolvedIntent = try {
                route.resolveUri(uri)
            } catch (e: Exception) {
                log.e(
                    "Route ${route.javaClass.simpleName} threw ${e.javaClass.name} while resolving " +
                        "`${uri?.forLogging()}`, skipping it",
                )
                null
            }
            if (resolvedIntent != null) {
                return resolvedIntent
            }
        }
        return null
    }

    override fun registerRoute(route: Route) {
        registeredRoutes.add(route)
    }

    /**
     * Scheme, host and port, and path. User info is dropped, and an authority that did not parse
     * into user info, host and port (a registry-based one) is redacted whole, since it cannot be
     * split safely. Relative URIs keep their leading path and opaque ones (`mailto:`, `tel:`)
     * reduce to their scheme, rather than either being dressed up as `scheme://`.
     */
    private fun URI.forLogging(): String = buildString {
        scheme?.let { append(it).append(':') }
        if (rawAuthority != null) {
            append("//")
            when {
                host == null -> append("<authority>")
                port == -1 -> append(host)
                else -> append(host).append(':').append(port)
            }
        }
        append(rawPath.orEmpty())
    }
}
