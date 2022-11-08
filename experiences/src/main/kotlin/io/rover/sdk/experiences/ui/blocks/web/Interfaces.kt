package io.rover.sdk.experiences.ui.blocks.web

import io.rover.sdk.experiences.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.experiences.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.sdk.experiences.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.sdk.experiences.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.sdk.experiences.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.sdk.experiences.ui.concerns.MeasuredBindableView
import io.rover.sdk.experiences.ui.concerns.BindableViewModel
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
