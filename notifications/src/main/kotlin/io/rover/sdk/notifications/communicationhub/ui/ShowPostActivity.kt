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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import io.rover.sdk.core.Rover
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.routing.LinkOpenInterface
import io.rover.sdk.notifications.communicationhub.data.database.entities.PostWithSubscription
import io.rover.sdk.notifications.roverEngageRepository
import io.rover.sdk.notifications.ui.screens.PostDetail

/**
 * An activity meant for presenting a single Post full-screen (modally), particularly
 * in the context of link routing. See [io.rover.sdk.notifications.communicationhub.routing.ShowPostRoute].
 */
class ShowPostActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract post ID from intent extras (for deep linking)
        val postId = intent.getStringExtra(EXTRA_POST_ID)

        if (postId == null) {
            // If no post ID is provided, log an error and finish the activity
            log.e("ShowPostActivity: No post ID provided in intent extras.")
            finish()
            return
        }

        setContent {
            // because this activity is used outside of the context of the customer's app's
            // compose UI, and thus its MaterialTheme setup, we offer the global setters
            // [Rover.shared.lightColorScheme] and [Rover.shared.darkColorScheme] to allow
            // the developer to provide their color scheme globally.
            val colorScheme = if (isSystemInDarkTheme()) {
                Rover.shared.darkColorScheme
            } else {
                Rover.shared.lightColorScheme
            }

            val postForTitle by androidx.compose.runtime.produceState<PostWithSubscription?>(
                initialValue = null,
                key1 = postId
            ) {
                value = postId?.let { Rover.shared.roverEngageRepository.getPostWithSubscriptionById(it) }
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background,
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = postForTitle?.post?.subject ?: "Post",
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                },
                                navigationIcon = {
                                    androidx.compose.material3.IconButton(onClick = { finish() }) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        PostDetail(
                            postId = postId,
                            postsRepository = Rover.shared.roverEngageRepository,
                            onBackClick = { finish() },
                            onOpenUrl = { url ->
                                val linkOpen = Rover.shared.resolve(LinkOpenInterface::class.java)
                                // Use Rover's link open interface to handle external links
                                val uri = url.toUri()
                                linkOpen?.intentForLink(this, uri)?.let { intent ->
                                    startActivity(intent)
                                }
                            },
                            onErrorDismiss = { finish() },
                            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding).fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_POST_ID = "post_id"

        /**
         * Creates an intent to open the Post Detail with a specific post.
         * This replaces CommunicationHubDetailActivity.makeIntent()
         */
        fun makeIntent(context: Context, uri: Uri?, postId: String): Intent {
            return Intent(context, ShowPostActivity::class.java).apply {
                data = uri
                putExtra(EXTRA_POST_ID, postId)
            }
        }
    }
}