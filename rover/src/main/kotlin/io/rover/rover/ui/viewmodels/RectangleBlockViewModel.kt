package io.rover.rover.ui.viewmodels

import io.rover.rover.core.domain.RectangleBlock
import io.rover.rover.ui.types.ViewType


class RectangleBlockViewModel(
    block: RectangleBlock
) : RectangleBlockViewModelInterface, BlockViewModel(block), BackgroundViewModelInterface by BackgroundViewModel(block) {
    override val viewType: ViewType = ViewType.Rectangle
}
