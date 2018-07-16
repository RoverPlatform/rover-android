package io.rover.experiences.ui.blocks.barcode

import io.rover.experiences.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.experiences.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.experiences.ui.blocks.concerns.layout.*
import io.rover.core.ui.concerns.BindableView
import io.rover.core.ui.concerns.BindableViewModel

interface ViewBarcodeInterface: BindableView<BarcodeViewModelInterface>

interface BarcodeViewModelInterface : Measurable, LayoutPaddingDeflection, BindableViewModel {
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
    BackgroundViewModelInterface,
    BarcodeViewModelInterface,
    BorderViewModelInterface