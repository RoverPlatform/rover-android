package io.rover.sdk.ui.blocks.barcode

import io.rover.sdk.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.sdk.ui.layout.ViewType

class BarcodeBlockViewModel(
    private val blockViewModel: BlockViewModelInterface,
    private val barcodeViewModel: BarcodeViewModelInterface
) : BarcodeBlockViewModelInterface,
    BlockViewModelInterface by blockViewModel,
    BarcodeViewModelInterface by barcodeViewModel {
    override val viewType: ViewType = ViewType.Barcode
}
