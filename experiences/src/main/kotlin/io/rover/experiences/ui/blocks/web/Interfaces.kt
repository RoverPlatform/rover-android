package io.rover.experiences.ui.blocks.web

import io.rover.experiences.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.experiences.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.experiences.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.experiences.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.experiences.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.core.ui.concerns.BindableView
import io.rover.core.ui.concerns.BindableViewModel
import java.net.URL

interface ViewWebInterface: BindableView<WebViewModelInterface>

interface WebViewModelInterface: BindableViewModel {
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
