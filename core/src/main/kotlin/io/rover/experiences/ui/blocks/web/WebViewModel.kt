package io.rover.experiences.ui.blocks.web

import io.rover.experiences.data.domain.WebView
import io.rover.experiences.data.domain.WebViewBlock
import java.net.URL

class WebViewModel(
    private val webView: WebView
) : WebViewModelInterface {

    override val url: URL
        get() = webView.url

    override val scrollingEnabled: Boolean
        get() = webView.isScrollingEnabled
}
