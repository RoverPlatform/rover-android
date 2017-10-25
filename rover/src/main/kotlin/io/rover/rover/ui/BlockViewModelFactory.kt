package io.rover.rover.ui

import io.rover.rover.core.domain.Block
import io.rover.rover.core.domain.RectangleBlock
import io.rover.rover.ui.viewmodels.BlockViewModelInterface
import io.rover.rover.ui.viewmodels.RectangleBlockViewModel

interface BlockViewModelFactoryInterface {
    fun viewModelForBlock(block: Block): BlockViewModelInterface
}

class BlockViewModelFactory: BlockViewModelFactoryInterface {
    override fun viewModelForBlock(block: Block): BlockViewModelInterface {
        return when(block) {
            is RectangleBlock -> RectangleBlockViewModel(block)
            else -> throw Exception(
                "This Rover UI block type is not yet supported by the 2.0 SDK: ${block.javaClass.simpleName}."
            )
        }
    }
}
