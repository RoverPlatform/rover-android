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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rover.sdk.core.Rover
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.events.domain.Event
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.routing.LinkOpenInterface
import io.rover.sdk.experiences.rich.compose.ui.LocalExternalNavController
import io.rover.sdk.notifications.communicationhub.openLink
import io.rover.sdk.notifications.communicationhub.ui.ClearHubRouteVisibilityOnDispose
import io.rover.sdk.notifications.communicationhub.navigation.HubCoordinator
import io.rover.sdk.notifications.communicationhub.navigation.HubNavigationState
import io.rover.sdk.notifications.communicationhub.ui.reportHubRouteVisibility
import io.rover.sdk.notifications.conversationPushNotificationPresenter

@Composable
internal fun ConversationDetailRoute(
    conversationId: String,
    conversationsRepository: ConversationsDataSource,
    conversationsSync: ConversationsHistorySync,
    hubCoordinator: HubCoordinator,
    linkOpen: LinkOpenInterface?,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val navController = LocalExternalNavController.current
    val navigationState = HubNavigationState.ShowingConversation(conversationId)
    val routeVisibilityModifier = Modifier.reportHubRouteVisibility(hubCoordinator, navigationState)
    ClearHubRouteVisibilityOnDispose(hubCoordinator, navigationState)
    val viewModel: ConversationDetailViewModel = viewModel(key = conversationId) {
        ConversationDetailViewModel(
            conversationsRepository = conversationsRepository,
            conversationsSync = conversationsSync,
            conversationId = conversationId,
            conversationNotificationPresenter = Rover.shared.conversationPushNotificationPresenter,
        )
    }
    val uiState by viewModel.uiState.collectAsState()
    val clearOnRevealModifier = Modifier.clearConversationNotificationOnReveal(viewModel)

    // Track a "Conversation Opened" analytics event once per opened conversation.
    LaunchedEffect(conversationId) {
        val eventQueueService =
            Rover.shared.resolveSingletonOrFail(EventQueueServiceInterface::class.java)
        val event = Event(
            name = "Conversation Opened",
            attributes = mapOf("conversationID" to conversationId),
        )
        eventQueueService.trackEvent(event, "rover")
        log.d("Tracked Conversation Opened event for conversationID: $conversationId")
    }

    ConversationDetailDismissEffect(viewModel.effects) { navController?.popBackStack() }

    LaunchedEffect(uiState.replies.lastOrNull()?.id) {
        viewModel.onLatestReplyVisible(uiState.replies.lastOrNull()?.id)
    }

    Surface(
        modifier = routeVisibilityModifier
            .then(clearOnRevealModifier)
            .fillMaxSize()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding),
        color = MaterialTheme.colorScheme.background,
    ) {
        ConversationDetail(
            uiState = uiState,
            onComposerTextChanged = viewModel::onComposerTextChanged,
            onLoadOlderRequested = viewModel::onLoadOlderRequested,
            onSendTapped = viewModel::onSendTapped,
            onOpenUrl = { url -> linkOpen?.openLink(url, context) },
        )
    }
}

internal fun Modifier.clearConversationNotificationOnReveal(
    viewModel: ConversationDetailViewModel,
): Modifier = this.onVisibilityChanged(minFractionVisible = 1f) { isVisible ->
    if (isVisible) {
        viewModel.revealed()
    }
}
