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

package io.rover.sdk.experiences

import io.rover.sdk.experiences.data.URLRequest
import java.net.URL

/**
 * This object is responsible for holding references to HTTP data source authorizers
 * (that can rewrite [URLREquest]
 */
public class Authorizers {
    private val authorizers: MutableList<Authorizer> = mutableListOf()

    /**
     * Call this method to register a callback that can mutate outgoing HTTP requests to
     * Data Source APIs being used in Experiences.
     *
     * Use this to add your own custom authentication headers for API keys, etc.
     */
    fun registerAuthorizer(pattern: String, callback: (URLRequest) -> Unit) {
        authorizers.add(
            Authorizer(pattern, callback)
        )
    }

    internal class Authorizer(
        private val pattern: String,
        private val block: (URLRequest) -> Unit
    ) {
        fun authorize(request: URLRequest) {
            val host = URL(request.url).host

            val wildcardAndRoot = pattern.split("*.")
            if (wildcardAndRoot.size > 2) return

            val root = wildcardAndRoot.lastOrNull() ?: return

            val hasWildcard = wildcardAndRoot.size > 1

            if ((!hasWildcard && host == pattern) || (hasWildcard && (host == root || host.endsWith(".$root")))) {
                block(request)
            }
        }
    }

    /**
     * Apply all the applicable authorizers to the given [URLRequest].
     */
    internal fun authorize(request: URLRequest) {
        authorizers.forEach { it.authorize(request) }
    }
}
