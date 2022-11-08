package io.rover.sdk.experiences.ui.blocks.barcode

import io.rover.sdk.experiences.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.sdk.experiences.ui.layout.ViewType

internal class BarcodeBlockViewModel(
        private val blockViewModel: BlockViewModelInterface,
        private val barcodeViewModel: BarcodeViewModelInterface
) : BarcodeBlockViewModelInterface,
    BlockViewModelInterface by blockViewModel,
    BarcodeViewModelInterface by barcodeViewModel {
    override val viewType: ViewType = ViewType.Barcode
}
