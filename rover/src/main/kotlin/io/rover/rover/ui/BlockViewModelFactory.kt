package io.rover.rover.ui

import io.rover.rover.core.domain.Block
import io.rover.rover.core.domain.ImageBlock
import io.rover.rover.core.domain.RectangleBlock
import io.rover.rover.core.domain.TextBlock
import io.rover.rover.services.assets.AssetService
import io.rover.rover.ui.viewmodels.BackgroundViewModel
import io.rover.rover.ui.viewmodels.BlockViewModelInterface
import io.rover.rover.ui.viewmodels.BorderViewModel
import io.rover.rover.ui.viewmodels.ImageBlockViewModel
import io.rover.rover.ui.viewmodels.RectangleBlockViewModel
import io.rover.rover.ui.viewmodels.TextBlockViewModel

interface BlockViewModelFactoryInterface {
    fun viewModelForBlock(block: Block): BlockViewModelInterface
}

class BlockViewModelFactory(
    private val measurementService: MeasurementService,
    private val assetService: AssetService
): BlockViewModelFactoryInterface {
    override fun viewModelForBlock(block: Block): BlockViewModelInterface {
        return when(block) {
            // TODO: gonna have to start injecting all of the delegated viewmodels here.  The
            // upcoming DI backplane is gonna have its work cut out for it.
            is RectangleBlock -> RectangleBlockViewModel(block)
            is TextBlock -> TextBlockViewModel(
                block,
                measurementService,
                BackgroundViewModel(block),
                BorderViewModel(block)
            )
            is ImageBlock -> ImageBlockViewModel(
                block,
                assetService,
                BackgroundViewModel(block),
                BorderViewModel(block)
            )
            else -> throw Exception(
                "This Rover UI block type is not yet supported by the 2.0 SDK: ${block.javaClass.simpleName}."
            )
        }
    }
}
