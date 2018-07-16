package io.rover.experiences.ui.blocks.barcode

import io.rover.core.data.domain.Barcode
import io.rover.core.data.domain.BarcodeFormat
import io.rover.experiences.MeasurementService
import io.rover.experiences.ui.blocks.concerns.layout.Padding
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

    override val paddingDeflection: Padding
        get() = when (barcode.format) {
            BarcodeFormat.Pdf417 -> Padding(5, 5, 5, 5)
            else -> Padding(20, 20, 20, 20)
        }
}
