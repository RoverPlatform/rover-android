package io.rover.sdk.experiences.ui.blocks.web

import io.rover.sdk.core.data.domain.WebView
import java.net.URL

internal class WebViewModel(
    private val webView: WebView
) : WebViewModelInterface {

    override val url: URL
        get() = webView.url

    override val scrollingEnabled: Boolean
        get() = webView.isScrollingEnabled
}
