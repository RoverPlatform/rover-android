package io.rover.experiences.ui.blocks.barcode

import io.rover.experiences.ui.layout.ViewType
import io.rover.experiences.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.experiences.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.experiences.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.experiences.ui.blocks.concerns.layout.Padding

class BarcodeBlockViewModel(
    private val blockViewModel: BlockViewModelInterface,
    private val barcodeViewModel: BarcodeViewModelInterface,
    private val backgroundViewModel: BackgroundViewModelInterface,
    private val borderViewModel: BorderViewModelInterface
) : BarcodeBlockViewModelInterface,
    BlockViewModelInterface by blockViewModel,
    BackgroundViewModelInterface by backgroundViewModel,
    BorderViewModelInterface by borderViewModel,
    BarcodeViewModelInterface by barcodeViewModel {
    override val viewType: ViewType = ViewType.Barcode

    override val paddingDeflection: Padding
        // Both the border and the barcode itself are contributing insets/padding, so add them
        // together.
        get() = Padding(
            borderViewModel.paddingDeflection.left + barcodeViewModel.paddingDeflection.left,
            borderViewModel.paddingDeflection.top + barcodeViewModel.paddingDeflection.top,
            borderViewModel.paddingDeflection.right + barcodeViewModel.paddingDeflection.right,
            borderViewModel.paddingDeflection.bottom + barcodeViewModel.paddingDeflection.bottom
        )
}
