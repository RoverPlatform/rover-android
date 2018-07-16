package io.rover.experiences.ui.blocks.web

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import io.rover.core.ui.concerns.BindableView
import io.rover.core.ui.concerns.ViewModelBinding

@SuppressLint("SetJavaScriptEnabled")
class ViewWeb(
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

        // TODO disable the scroll bars if scrolling is disabled, because otherwise they'll appear
        // when you scroll by
    }

    override var viewModel: BindableView.Binding<WebViewModelInterface>? by ViewModelBinding { binding, _ ->
        webView.loadUrl(
            binding?.viewModel?.url?.toString() ?: "about://blank"
        )
    }
}
