package io.rover.experiences.ui.blocks.web

import io.rover.core.data.domain.WebViewBlock
import java.net.URL

class WebViewModel(
    private val webViewBlock: WebViewBlock
) : WebViewModelInterface {

    override val url: URL
        get() = webViewBlock.webView.url

    override val scrollingEnabled: Boolean
        get() = webViewBlock.webView.isScrollingEnabled
}
