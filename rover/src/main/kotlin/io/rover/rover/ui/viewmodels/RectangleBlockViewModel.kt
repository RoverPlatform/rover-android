package io.rover.rover.ui.viewmodels

import io.rover.rover.core.domain.RectangleBlock
import io.rover.rover.ui.types.ViewType

class RectangleBlockViewModel(
    block: RectangleBlock,
    blockViewModel: BlockViewModelInterface
) : RectangleBlockViewModelInterface,
    BlockViewModelInterface by blockViewModel,
    BackgroundViewModelInterface by BackgroundViewModel(block),
    BorderViewModelInterface by BorderViewModel(block) {
    override val viewType: ViewType = ViewType.Rectangle
}
