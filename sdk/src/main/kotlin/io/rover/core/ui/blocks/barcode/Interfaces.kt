package io.rover.core.ui.blocks.barcode

import io.rover.core.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.core.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.core.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.core.ui.blocks.concerns.layout.Measurable
import io.rover.core.ui.concerns.BindableViewModel
import io.rover.core.ui.concerns.MeasuredBindableView

interface ViewBarcodeInterface : MeasuredBindableView<BarcodeViewModelInterface>

interface BarcodeViewModelInterface : Measurable, BindableViewModel {
    val barcodeType: BarcodeType

    val barcodeValue: String

    enum class BarcodeType {
        PDF417, Code128, Aztec, QrCode
    }
}

interface BarcodeBlockViewModelInterface :
    CompositeBlockViewModelInterface,
    LayoutableViewModel,
    BlockViewModelInterface,
    BarcodeViewModelInterface