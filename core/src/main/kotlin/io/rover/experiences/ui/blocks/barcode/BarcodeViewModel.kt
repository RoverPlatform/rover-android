package io.rover.experiences.ui.blocks.barcode

import io.rover.experiences.data.domain.Barcode
import io.rover.experiences.data.domain.BarcodeFormat
import io.rover.experiences.ui.RectF
import io.rover.experiences.MeasurementService

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
