package io.rover.core.ui.blocks.web

import io.rover.core.ui.layout.ViewType
import io.rover.core.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.core.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.core.ui.blocks.concerns.border.BorderViewModelInterface

class WebViewBlockViewModel(
    private val blockViewModel: BlockViewModelInterface,
    private val backgroundViewModel: BackgroundViewModelInterface,
    private val borderViewModel: BorderViewModelInterface,
    private val webViewModel: WebViewModelInterface
) : WebViewBlockViewModelInterface,
    BlockViewModelInterface by blockViewModel,
    BackgroundViewModelInterface by backgroundViewModel,
    BorderViewModelInterface by borderViewModel,
    WebViewModelInterface by webViewModel {
    override val viewType: ViewType = ViewType.WebView
}