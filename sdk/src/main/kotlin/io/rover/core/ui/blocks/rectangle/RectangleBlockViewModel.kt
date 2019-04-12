package io.rover.core.ui.blocks.rectangle

import io.rover.core.ui.layout.ViewType
import io.rover.core.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.core.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.core.ui.blocks.concerns.border.BorderViewModelInterface

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
