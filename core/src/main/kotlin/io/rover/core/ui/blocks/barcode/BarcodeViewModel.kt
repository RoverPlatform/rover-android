package io.rover.core.ui.blocks.barcode

import io.rover.core.services.MeasurementService
import io.rover.core.data.domain.Barcode
import io.rover.core.data.domain.BarcodeFormat
import io.rover.core.ui.RectF

/**
 * Barcode display view model.
 */
class BarcodeViewModel(
    private val barcode: Barcode,
    private val measurementService: MeasurementService
) : BarcodeViewModelInterface {
    override val barcodeType: BarcodeViewModelInterface.BarcodeType
        get() = when (barcode.format) {
            BarcodeFormat.AztecCode -> BarcodeViewModelInterface.BarcodeType.Aztec
            BarcodeFormat.Code128 -> BarcodeViewModelInterface.BarcodeType.Code128
            BarcodeFormat.Pdf417 -> BarcodeViewModelInterface.BarcodeType.PDF417
            BarcodeFormat.QrCode -> BarcodeViewModelInterface.BarcodeType.QrCode
        }

    override val barcodeValue: String
        get() = barcode.text

    override fun intrinsicHeight(bounds: RectF): Float {
        return measurementService.measureHeightNeededForBarcode(
            barcode.text,
            barcodeType,
            bounds.width()
        )
    }
}
