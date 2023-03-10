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

package io.rover.sdk.experiences.classic.blocks.web

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView
import io.rover.sdk.experiences.classic.concerns.ViewModelBinding

@SuppressLint("SetJavaScriptEnabled")
internal class ViewWeb(
    private val webView: WebView
) : ViewWebInterface {

    init {
        WebView.setWebContentsDebuggingEnabled(true)
        // Setting an otherwise unconfigured WebViewClient will have the webview navigate (follow
        // links) internally.
        webView.webViewClient = WebViewClient()
        webView.settings.domStorageEnabled = true

        // Suppressed SetJavaScriptEnabled, because we do not expose any custom native APIs to
        // Javascript, and thus no extra security surface beyond that of a standard web browser are
        // introduced.
        webView.settings.javaScriptEnabled = true

        // Allow for media to auto play
        webView.settings.mediaPlaybackRequiresUserGesture = false

        // TODO disable the scroll bars if scrolling is disabled, because otherwise they'll appear
        // when you scroll by
    }

    override var viewModelBinding: MeasuredBindableView.Binding<WebViewModelInterface>? by ViewModelBinding { binding, _ ->
        webView.loadUrl(
            binding?.viewModel?.url?.toString() ?: "about://blank"
        )
    }
}
