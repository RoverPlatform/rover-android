package io.rover.core.ui.blocks.web

import io.rover.core.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.core.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.core.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.core.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.core.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.core.ui.concerns.MeasuredBindableView
import io.rover.core.ui.concerns.BindableViewModel
import java.net.URL

interface ViewWebInterface : MeasuredBindableView<WebViewModelInterface>

interface WebViewModelInterface : BindableViewModel {
    val url: URL
    val scrollingEnabled: Boolean
}

interface WebViewBlockViewModelInterface :
    CompositeBlockViewModelInterface,
    LayoutableViewModel,
    BlockViewModelInterface,
    BackgroundViewModelInterface,
    BorderViewModelInterface,
    WebViewModelInterface
