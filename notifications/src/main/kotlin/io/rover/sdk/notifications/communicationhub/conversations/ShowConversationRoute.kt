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

package io.rover.sdk.notifications.communicationhub.conversations

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import io.rover.sdk.core.data.config.ConfigManager
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.routing.Route
import io.rover.sdk.notifications.communicationhub.navigation.HubCoordinator
import io.rover.sdk.notifications.communicationhub.ui.ShowConversationActivity
import java.net.URI

/**
 * Route handler for Conversation deep links.
 *
 * Handles:
 * - rv-myapp://conversations/{id} -> Coordinates with HubCoordinator for in-app navigation,
 *                                    or opens standalone ShowConversationActivity
 *
 * When a hub deep link is configured, this route queues navigation through the HubCoordinator
 * and returns an ACTION_VIEW intent for the host app's deep link URI. Otherwise it falls
 * back to launching ShowConversationActivity directly.
 */
internal class ShowConversationRoute(
    private val context: Context,
    private val urlSchemes: Set<String>,
    private val hubCoordinator: HubCoordinator,
    private val configManager: ConfigManager,
) : Route {
    override fun resolveUri(uri: URI?): Intent? {
        if (uri == null) {
            return null
        }

        if (!urlSchemes.contains(uri.scheme?.lowercase()) || uri.authority?.lowercase() != "conversations") {
            return null
        }

        val pathSegments = uri.path?.split("/")?.filter { it.isNotEmpty() } ?: emptyList()
        if (pathSegments.size != 1) {
            return null
        }

        val conversationId = pathSegments.first()
        val androidUri = uri.toString().toUri()

        return revealConversationInHub(conversationId, configManager, hubCoordinator)
            ?: run {
                log.v("Conversations: Using standalone activity for conversation $conversationId")
                hubCoordinator.navigateToConversation(conversationId)
                ShowConversationActivity.makeIntent(context, androidUri, conversationId)
            }
    }

    companion object {
        /**
         * If the Reveal Rover hub deep link is configured, queues navigation to [conversationId]
         * via [HubCoordinator] and returns an ACTION_VIEW intent to request the host app to
         * reveal the Rover Hub. The returned intent must be dispatched immediately — the
         * coordinator state has already been updated. Returns null when hub navigation is not
         * configured; callers should fall back to standalone conversation display.
         */
        internal fun revealConversationInHub(
            conversationId: String,
            configManager: ConfigManager,
            hubCoordinator: HubCoordinator,
        ): Intent? {
            val config = configManager.config.value
            val hubDeepLink = config.hub.deepLink
                ?.takeIf { config.hub.isInboxEnabled && it.isNotBlank() }
                ?: return null
            log.v("Conversations: Using host app deep link coordination for conversation $conversationId")
            hubCoordinator.navigateToConversation(conversationId)
            return Intent(Intent.ACTION_VIEW, Uri.parse(hubDeepLink)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
