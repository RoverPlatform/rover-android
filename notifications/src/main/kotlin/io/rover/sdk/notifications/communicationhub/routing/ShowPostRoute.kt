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

package io.rover.sdk.notifications.communicationhub.routing

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.routing.Route
import io.rover.sdk.notifications.communicationhub.ui.ShowPostActivity
import java.net.URI
import androidx.core.net.toUri

/**
 * Route handler for Posts deep links using [ShowPostActivity].
 *
 * Handles:
 * - rv-myapp://posts/{id} -> Opens standalone posts with specific post
 */
internal class ShowPostRoute(
    private val context: Context,
    private val urlSchemes: Set<String>
) : Route {

    override fun resolveUri(uri: URI?): Intent? {
        if (uri == null) {
            return null
        }
        
        if (!urlSchemes.contains(uri.scheme?.lowercase()) || 
            (uri.authority != "inbox" && uri.authority != "posts")) {
            return null
        }

        val pathSegments = uri.path?.split("/")?.filter { it.isNotEmpty() } ?: emptyList()

        val androidUri = uri.toString().toUri()

        return when (uri.authority) {
            "posts" -> {
                when {
                    pathSegments.size == 1 -> {
                        // rover://posts/{id} -> Opens standalone posts with specific post
                        val postId = pathSegments[0]
                        log.v("Posts List: Opening standalone post detail for ID: $postId")
                        ShowPostActivity.makeIntent(context, androidUri, postId)
                    }
                    else -> {
                        log.w("Posts List: Unknown posts path: ${uri.path}")
                        null
                    }
                }
            }
            else -> {
                log.w("Posts List: Unknown authority: ${uri.authority}")
                null
            }
        }
    }
}
