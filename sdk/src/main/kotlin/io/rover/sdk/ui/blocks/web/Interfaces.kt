package io.rover.sdk.ui.blocks.web

import io.rover.sdk.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.sdk.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.sdk.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.sdk.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.BindableViewModel
import java.net.URL

internal interface ViewWebInterface : MeasuredBindableView<WebViewModelInterface>

internal interface WebViewModelInterface : BindableViewModel {
    val url: URL
    val scrollingEnabled: Boolean
}

internal interface WebViewBlockViewModelInterface :
    CompositeBlockViewModelInterface,
    LayoutableViewModel,
    BlockViewModelInterface,
    BackgroundViewModelInterface,
    BorderViewModelInterface,
    WebViewModelInterface
