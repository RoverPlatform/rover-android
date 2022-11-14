package io.rover.sdk.experiences.ui.blocks.rectangle

import io.rover.sdk.experiences.ui.layout.ViewType
import io.rover.sdk.experiences.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.experiences.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.sdk.experiences.ui.blocks.concerns.border.BorderViewModelInterface

internal class RectangleBlockViewModel(
        blockViewModel: BlockViewModelInterface,
        backgroundViewModel: BackgroundViewModelInterface,
        borderViewModel: BorderViewModelInterface
) : RectangleBlockViewModelInterface,
    BlockViewModelInterface by blockViewModel,
    BackgroundViewModelInterface by backgroundViewModel,
    BorderViewModelInterface by borderViewModel {
    override val viewType: ViewType = ViewType.Rectangle
}
