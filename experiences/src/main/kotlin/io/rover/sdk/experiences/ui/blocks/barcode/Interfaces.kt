package io.rover.sdk.experiences.ui.blocks.barcode

import io.rover.sdk.experiences.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.sdk.experiences.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.sdk.experiences.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.sdk.experiences.ui.blocks.concerns.layout.Measurable
import io.rover.sdk.experiences.ui.concerns.BindableViewModel
import io.rover.sdk.experiences.ui.concerns.MeasuredBindableView

internal interface ViewBarcodeInterface : MeasuredBindableView<BarcodeViewModelInterface>

internal interface BarcodeViewModelInterface : Measurable, BindableViewModel {
    val barcodeType: BarcodeType

    val barcodeValue: String

    enum class BarcodeType {
        PDF417, Code128, Aztec, QrCode
    }
}

internal interface BarcodeBlockViewModelInterface :
    CompositeBlockViewModelInterface,
        LayoutableViewModel,
        BlockViewModelInterface,
        BarcodeViewModelInterface