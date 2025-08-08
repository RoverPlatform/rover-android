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

package io.rover.sdk.notifications.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rover.sdk.core.Rover
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.events.domain.Event
import io.rover.sdk.core.logging.log
import io.rover.sdk.notifications.communicationhub.data.database.entities.PostEntity
import io.rover.sdk.notifications.communicationhub.data.database.entities.PostWithSubscription
import io.rover.sdk.notifications.ui.viewmodels.PostDetailViewModel
import java.util.Locale


@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun PostDetail(
    post: PostWithSubscription,
    postsRepository: io.rover.sdk.notifications.communicationhub.data.repository.CommHubRepository,
    onBackClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onErrorDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // using key() to ensure PostDetail is not reused for different posts
    key(post.post.id) {
        val viewModel: PostDetailViewModel = viewModel(key = post.post.id) {
            PostDetailViewModel(postsRepository, post.post.id)
        }

        val uiState by viewModel.uiState.collectAsState()

        val postEntity = uiState.post ?: post.post


        PostDetailContent(
            post = postEntity,
            onBackClick = onBackClick,
            onOpenUrl = onOpenUrl,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun PostDetail(
    postId: String,
    postsRepository: io.rover.sdk.notifications.communicationhub.data.repository.CommHubRepository,
    onBackClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onErrorDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // using key() to ensure PostDetail is not reused for different posts
    key(postId) {
        val viewModel: PostDetailViewModel = viewModel(key = postId) {
            PostDetailViewModel(postsRepository, postId)
        }

        val uiState by viewModel.uiState.collectAsState()

        when {
            uiState.isLoading -> {
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            uiState.error != null -> {
                // Show AlertDialog for error
                AlertDialog(
                    onDismissRequest = onErrorDismiss,
                    title = { Text("Error") },
                    text = { Text("The requested post could not be found.") },
                    confirmButton = {
                        TextButton(onClick = onErrorDismiss) {
                            Text("OK")
                        }
                    }
                )
            }
            uiState.post != null -> {
                PostDetailContent(
                    post = uiState.post!!,
                    onBackClick = onBackClick,
                    onOpenUrl = onOpenUrl,
                    modifier = modifier
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostDetailContent(
    post: PostEntity,
    onBackClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val eventQueueService = Rover.shared.resolveSingletonOrFail(EventQueueServiceInterface::class.java)

    LaunchedEffect(post.id) {
        val event = Event(
            name = "Post Opened",
            attributes = mapOf("postID" to post.id)
        )
        eventQueueService.trackEvent(event, "rover")
        log.d("Tracked Post Opened event for postID: ${post.id}")
    }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(post.subject, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        PostDetailWebView(
            post = post,
            eventQueueService = eventQueueService,
            onOpenUrl = onOpenUrl,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PostDetailWebView(
    post: PostEntity,
    eventQueueService: EventQueueServiceInterface,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    
    post.url?.let { url ->
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    log.d("Loading URL: $url")
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    // necessary for shouldOverrideUrlLoading():
                    settings.setSupportMultipleWindows(false)

                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            url?.let { clickedUrl ->
                                if (clickedUrl != post.url) {
                                    val event = Event(
                                        name = "Post Link Clicked",
                                        attributes = mapOf(
                                            "postID" to post.id.toString(),
                                            "link" to clickedUrl
                                        )
                                    )
                                    eventQueueService.trackEvent(event, "rover")
                                    log.d("Tracked Post Link Clicked event for postID: ${post.id}, link: $clickedUrl")
                                    
                                    onOpenUrl(clickedUrl)
                                    return true
                                }
                            }
                            return false
                        }
                        
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Inject accent color CSS after page load

                            // get primary color from Material 3 theme
                            val accentColor = colorScheme.primary

                            if (accentColor != Color.Unspecified) {
                                val cssScript = generateStylesJavaScript(accentColor, colorScheme)
                                view?.evaluateJavascript(cssScript, null)
                            }
                        }
                    }
                    
                    loadUrl(url)
                }
            },
            modifier = modifier.fillMaxSize()
        )
    }
}

private fun generateStylesJavaScript(
    accentColor: Color,
    colorScheme: ColorScheme
): String {
    val colorString = accentColor.resolveColorToCssString(colorScheme)

    return """
        document.documentElement.style.setProperty('--accent-color', '$colorString');
        console.log('Accent color updated: $colorString');
    """.trimIndent()
}

private fun Color.resolveColorToCssString(colorScheme: ColorScheme): String {
    if (this == Color.Unspecified) {
        return "color(srgb 0 0 0)" // Default to black if unspecified
    }
    
    val argb = this.toArgb()
    val r = ((argb shr 16) and 0xFF) / 255.0f
    val g = ((argb shr 8) and 0xFF) / 255.0f
    val b = (argb and 0xFF) / 255.0f
    val a = ((argb shr 24) and 0xFF) / 255.0f
    
    return if (a < 1.0f) {
        String.format(Locale.US, "color(srgb %.6f %.6f %.6f / %.6f)", r, g, b, a)
    } else {
        String.format(Locale.US, "color(srgb %.6f %.6f %.6f)", r, g, b)
    }
}

