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

package io.rover.sdk.notifications.communicationhub.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rover.sdk.core.Rover
import io.rover.sdk.core.data.config.ConfigManager
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.routing.LinkOpenInterface
import io.rover.sdk.notifications.communicationhub.openLink
import io.rover.sdk.notifications.communicationhub.rememberCommHubDarkTheme
import io.rover.sdk.notifications.communicationhub.conversations.ConversationDetailDismissEffect
import io.rover.sdk.notifications.communicationhub.conversations.ShowConversationRoute
import io.rover.sdk.notifications.communicationhub.navigation.HubCoordinator
import io.rover.sdk.notifications.conversationPushNotificationPresenter
import io.rover.sdk.notifications.conversationsRepository
import io.rover.sdk.notifications.conversationsSync
import io.rover.sdk.notifications.standaloneConversationVisibilityTracker
import io.rover.sdk.notifications.communicationhub.conversations.ConversationDetail
import io.rover.sdk.notifications.communicationhub.conversations.ConversationDetailViewModel

/**
 * Entry point for opening a conversation from a notification tap. If the host app has configured
 * a Hub deep link, this activity acts as a trampoline: it queues navigation via [HubCoordinator]
 * and immediately redirects to the hub, never rendering UI itself. When no hub deep link is
 * configured it falls back to presenting the conversation thread full-screen.
 */
class ShowConversationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        if (conversationId.isNullOrBlank()) {
            log.e("ShowConversationActivity: No conversation ID provided in intent extras.")
            finish()
            return
        }

        val configManager = Rover.shared.resolve(ConfigManager::class.java)
        val hubCoordinator = Rover.shared.resolve(HubCoordinator::class.java)
        if (configManager != null && hubCoordinator != null) {
            val hubIntent = ShowConversationRoute.revealConversationInHub(conversationId, configManager, hubCoordinator)
            if (hubIntent != null) {
                startActivity(hubIntent)
                finish()
                return
            }
        }

        setContent {
            ConversationStandaloneFallback(
                conversationId = conversationId,
                onDismiss = { finish() },
            )
        }
    }

    companion object {
        private const val EXTRA_CONVERSATION_ID = "conversation_id"

        fun makeIntent(context: Context, uri: Uri?, conversationId: String): Intent {
            return Intent(context, ShowConversationActivity::class.java).apply {
                data = uri
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
            }
        }
    }
}

/**
 * Full-screen conversation UI used when no Hub deep link is configured. Rendered by
 * [ShowConversationActivity] as a self-contained modal — the user dismisses it with the back
 * button or when the ViewModel emits a [ConversationDetailEffect.Dismiss] event.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationStandaloneFallback(
    conversationId: String,
    onDismiss: () -> Unit,
) {
    val colorScheme = if (rememberCommHubDarkTheme()) {
        Rover.shared.darkColorScheme
    } else {
        Rover.shared.lightColorScheme
    }
    val standaloneConversationVisibilityTracker = Rover.shared.standaloneConversationVisibilityTracker
    val linkOpen = Rover.shared.resolve(LinkOpenInterface::class.java)
    val context = LocalContext.current

    val viewModel: ConversationDetailViewModel = viewModel(key = conversationId) {
        ConversationDetailViewModel(
            conversationsRepository = Rover.shared.conversationsRepository,
            conversationsSync = Rover.shared.conversationsSync,
            conversationId = conversationId,
            conversationNotificationPresenter = Rover.shared.conversationPushNotificationPresenter,
        )
    }
    val uiState by viewModel.uiState.collectAsState()
    val standaloneVisibilityModifier = Modifier.onVisibilityChanged(minFractionVisible = 1f) { isVisible ->
        standaloneConversationVisibilityTracker.updateConversationVisibility(conversationId, isVisible)
    }
    val clearOnRevealModifier = Modifier.onVisibilityChanged(minFractionVisible = 1f) { isVisible ->
        if (isVisible) {
            viewModel.revealed()
        }
    }

    ConversationDetailDismissEffect(viewModel.effects) { onDismiss() }

    LaunchedEffect(uiState.replies.lastOrNull()?.id) {
        viewModel.onLatestReplyVisible(uiState.replies.lastOrNull()?.id)
    }

    DisposableEffect(conversationId, standaloneConversationVisibilityTracker) {
        onDispose {
            standaloneConversationVisibilityTracker.updateConversationVisibility(
                conversationId,
                isVisible = false,
            )
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = standaloneVisibilityModifier
                .then(clearOnRevealModifier)
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = uiState.title,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                ConversationDetail(
                    uiState = uiState,
                    onComposerTextChanged = viewModel::onComposerTextChanged,
                    onLoadOlderRequested = viewModel::onLoadOlderRequested,
                    onSendTapped = viewModel::onSendTapped,
                    onOpenUrl = { url -> linkOpen?.openLink(url, context) },
                    modifier = Modifier
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .fillMaxSize(),
                )
            }
        }
    }
}
