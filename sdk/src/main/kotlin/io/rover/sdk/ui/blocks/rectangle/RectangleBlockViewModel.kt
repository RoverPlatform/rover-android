package io.rover.sdk.ui.blocks.rectangle

import io.rover.sdk.ui.layout.ViewType
import io.rover.sdk.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.sdk.ui.blocks.concerns.border.BorderViewModelInterface

class RectangleBlockViewModel(
    blockViewModel: BlockViewModelInterface,
    backgroundViewModel: BackgroundViewModelInterface,
    borderViewModel: BorderViewModelInterface
) : RectangleBlockViewModelInterface,
    BlockViewModelInterface by blockViewModel,
    BackgroundViewModelInterface by backgroundViewModel,
    BorderViewModelInterface by borderViewModel {
    override val viewType: ViewType = ViewType.Rectangle
}
