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

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rover.sdk.core.Rover
import io.rover.sdk.core.data.sync.SyncCoordinatorInterface
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.events.domain.Event
import io.rover.sdk.core.logging.log
import io.rover.sdk.notifications.communicationhub.rememberCommHubDarkTheme
import java.util.Locale


@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun PostDetail(
    post: PostWithSubscription,
    postsRepository: PostsRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit,
    onErrorDismiss: () -> Unit = {},
) {
    // using key() to ensure PostDetail is not reused for different posts
    key(post.post.id) {
        val viewModel: PostDetailViewModel = viewModel(key = post.post.id) {
            PostDetailViewModel(
                postsRepository = postsRepository,
                syncCoordinator = Rover.shared.resolveSingletonOrFail(SyncCoordinatorInterface::class.java),
                postId = post.post.id,
            )
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
    postsRepository: PostsRepository,
    onBackClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onErrorDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // using key() to ensure PostDetail is not reused for different posts
    key(postId) {
        val viewModel: PostDetailViewModel = viewModel(key = postId) {
            PostDetailViewModel(
                postsRepository = postsRepository,
                syncCoordinator = Rover.shared.resolveSingletonOrFail(SyncCoordinatorInterface::class.java),
                postId = postId,
            )
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

    PostDetailWebView(
        post = post,
        eventQueueService = eventQueueService,
        onOpenUrl = onOpenUrl,
        modifier = modifier.fillMaxSize()
    )
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

    // The hosted post page styles its dark appearance via the CSS prefers-color-scheme media query.
    // On targetSdk 33+ WebView resolves that from the android:isLightTheme attribute of the theme of
    // its CURRENT Context (falling back to Configuration.uiMode only when the theme omits it) — not
    // from the surrounding Compose MaterialTheme. Here the construction context is also the only
    // context this WebView ever has, but beware when copying this pattern: Chromium re-evaluates the
    // query (at least on window attach), so a surface that later swaps the WebView's context — as App
    // Screens does at attach/detach — must keep the night bits AND a DayNight theme on every context
    // it installs (see experiences' AppScreenAppearance.kt). So the WebView needs a context with
    // both the night-mode bits and a DayNight theme matching the resolved scheme (AUTO follows the
    // device); this also frees host apps from needing an isLightTheme override in their own theme.
    // key(isDark) recreates the WebView when the resolved scheme changes, since AndroidView's factory
    // runs only once.
    val isDark = rememberCommHubDarkTheme()

    post.url?.let { url ->
        key(isDark) {
            AndroidView(
                factory = { context ->
                    val nightMode = if (isDark) {
                        Configuration.UI_MODE_NIGHT_YES
                    } else {
                        Configuration.UI_MODE_NIGHT_NO
                    }
                    val themedConfiguration = Configuration(context.resources.configuration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
                    }
                    val themedContext = ContextThemeWrapper(
                        context.createConfigurationContext(themedConfiguration),
                        android.R.style.Theme_DeviceDefault_DayNight
                    )
                    WebView(themedContext).apply {
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
