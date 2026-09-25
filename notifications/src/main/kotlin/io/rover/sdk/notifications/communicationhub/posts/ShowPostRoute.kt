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

package io.rover.sdk.notifications.communicationhub.posts

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.rover.sdk.core.data.config.ConfigManager
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.routing.Route
import io.rover.sdk.notifications.communicationhub.navigation.HubCoordinator
import io.rover.sdk.notifications.communicationhub.navigation.HubDeepLink
import io.rover.sdk.notifications.communicationhub.ui.ShowPostActivity
import java.net.URI

/**
 * Route handler for Posts deep links.
 *
 * Handles:
 * - rv-myapp://posts/{id} -> Coordinates with HubCoordinator for in-app navigation,
 *                            or opens standalone ShowPostActivity
 *
 * This route now supports deep link coordination with the Hub before it's presented on screen.
 * When a deep link arrives, it will queue navigation through the HubCoordinator, which the
 * Hub composable will execute when it's ready.
 *
 * When both a Hub deep link is configured and inbox is enabled, this route will use the
 * host app's deep link to reveal the Hub tab, providing a seamless navigation experience.
 */
internal class ShowPostRoute(
    private val context: Context,
    private val urlSchemes: Set<String>,
    private val hubCoordinator: HubCoordinator,
    private val configManager: ConfigManager
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
                        val postId = pathSegments[0]

                        val hubIntent = HubDeepLink.revealIntent(configManager, context)

                        if (hubIntent != null) {
                            log.v("Using host app deep link coordination for post $postId")
                            // Queue only on the coordination path. ShowPostActivity never consumes
                            // pending navigation, so queuing on the standalone path would leave the
                            // Hub to jump to an already-read post the next time it is presented.
                            //
                            // Queueing here rather than at dispatch is best-effort; see SDK-379.
                            hubCoordinator.navigateToPost(postId)
                            hubIntent
                        } else {
                            log.v("Using standalone activity for post $postId")
                            ShowPostActivity.makeIntent(context, androidUri, postId)
                        }
                    }
                    else -> {
                        log.w("Unknown posts path: ${uri.path}")
                        null
                    }
                }
            }
            "inbox" -> {
                // Handle inbox deep links
                log.v("Coordinating navigation to inbox")
                hubCoordinator.navigateToInbox()

                // Return null since inbox is handled by the Hub composable
                null
            }
            else -> {
                log.w("Unknown authority: ${uri.authority}")
                null
            }
        }
    }
}
