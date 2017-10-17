package io.rover.rover.ui

import io.rover.rover.core.domain.Block
import io.rover.rover.core.domain.RectangleBlock

class RectangleBlockViewModel(
    block: RectangleBlock
) : BlockViewModel(block) {
    override val viewType: ViewType = ViewType.Rectangle

    val backgroundColor = block.backgroundColor
}