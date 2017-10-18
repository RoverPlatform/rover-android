package io.rover.rover.ui

import io.rover.rover.core.domain.Block
import io.rover.rover.core.domain.RectangleBlock

interface RectangleBlockViewModelInterface: BlockViewModelInterface, BackgroundViewModelInterface

class RectangleBlockViewModel(
    block: RectangleBlock
) : RectangleBlockViewModelInterface, BlockViewModel(block), BackgroundViewModelInterface by BackgroundViewModel(block) {
    override val viewType: ViewType = ViewType.Rectangle
}
