package io.rover.core.ui.blocks.web

import io.rover.core.data.domain.WebView
import java.net.URL

class WebViewModel(
    private val webView: WebView
) : WebViewModelInterface {

    override val url: URL
        get() = webView.url

    override val scrollingEnabled: Boolean
        get() = webView.isScrollingEnabled
}
