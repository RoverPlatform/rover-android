package io.rover.rover.ui

import io.rover.rover.core.domain.Block
import io.rover.rover.core.domain.ImageBlock
import io.rover.rover.core.domain.RectangleBlock
import io.rover.rover.core.domain.TextBlock
import io.rover.rover.services.assets.AssetService
import io.rover.rover.ui.viewmodels.BackgroundViewModel
import io.rover.rover.ui.viewmodels.BlockViewModel
import io.rover.rover.ui.viewmodels.BlockViewModelInterface
import io.rover.rover.ui.viewmodels.BorderViewModel
import io.rover.rover.ui.viewmodels.ImageBlockViewModel
import io.rover.rover.ui.viewmodels.ImageViewModel
import io.rover.rover.ui.viewmodels.RectangleBlockViewModel
import io.rover.rover.ui.viewmodels.TextBlockViewModel
import io.rover.rover.ui.viewmodels.TextViewModel

interface BlockViewModelFactoryInterface {
    fun viewModelForBlock(block: Block): BlockViewModelInterface
}

class BlockViewModelFactory(
    private val measurementService: MeasurementService,
    private val assetService: AssetService
) : BlockViewModelFactoryInterface {
    override fun viewModelForBlock(block: Block): BlockViewModelInterface {
        return when (block) {
            is RectangleBlock -> RectangleBlockViewModel(block, BlockViewModel(block))
            is TextBlock -> {
                val textViewModel = TextViewModel(block, measurementService)
                val borderViewModel = BorderViewModel(block)
                TextBlockViewModel(
                    BlockViewModel(block, setOf(borderViewModel), textViewModel),
                    textViewModel,
                    BackgroundViewModel(block),
                    borderViewModel
                    // TODO: I would need to pass in some sort of measure-ator to blockviewmodel...
                    // actually, I could take the Text Concerns themselves out and put them into their own headless viewmodel, and have that implement
                    // the measure-ator interface :)
                )
            }
            is ImageBlock -> {
                val imageViewModel = ImageViewModel(block, assetService)
                val borderViewModel = BorderViewModel(block)
                ImageBlockViewModel(
                    BlockViewModel(block, setOf(borderViewModel), imageViewModel),
                    BackgroundViewModel(block),
                    imageViewModel,
                    borderViewModel
                )
            }
            else -> throw Exception(
                "This Rover UI block type is not yet supported by the 2.0 SDK: ${block.javaClass.simpleName}."
            )
        }
    }
}
