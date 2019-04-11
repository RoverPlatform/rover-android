package io.rover.experiences

import android.graphics.Bitmap
import io.rover.core.ui.blocks.barcode.BarcodeViewModelInterface
import io.rover.core.ui.blocks.concerns.text.Font
import io.rover.core.ui.blocks.concerns.text.FontAppearance

interface BarcodeRenderingServiceInterface {
    /**
     * Measure how much height a given bit of Unicode text will require if rendered as a barcode in
     * the specified format, and then meant to be scaled to fit the given width.
     *
     * @param format specifies what format of barcode should be used.
     *
     * Note that what length and sort of text is valid depends on the Barcode format.
     *
     * Returns the height needed to accommodate the barcode, at the correct aspect, at the given
     * width, in points.
     */
    fun measureHeightNeededForBarcode(
        text: String, format: Format, width: Float
    ): Float

    /**
     * Render the given string as a Barcode in the given format, pixel exact (not scaled).
     *
     * Note that what length and sort of text is valid depends on the Barcode format.
     */
    fun renderBarcode(
        text: String,
        format: Format
    ): Bitmap

    enum class Format {
        Pdf417, Code128, Aztec, QrCode
    }
}


interface MeasurementService {

    /**
     * Measure how much height a given bit of Unicode [richText] (with optional HTML tags such as
     * strong, italic, and underline) will require if soft wrapped to the given [width] and
     * [fontAppearance] ultimately meant to be displayed using an Android View.
     *
     * [boldFontAppearance] provides any font size or font-family modifications that should be
     * applied to bold text ( with no changes to colour or alignment).  This allows for nuanced
     * control of bold span styling.
     *
     * Returns the height needed to accommodate the text at the given width, in dps.
     */
    fun measureHeightNeededForRichText(
        richText: String,
        fontAppearance: FontAppearance,
        boldFontAppearance: Font,
        width: Float
    ): Float

    /**
     * Measure how much height a given bit of Unicode [text] will require if rendered as a barcode
     * in the given format.
     *
     * [type] specifies what format of barcode should be used, namely
     * [BarcodeViewModelInterface.BarcodeType]. Note that what length and sort of text is valid
     * depends on the type.
     *
     * Returns the height needed to accommodate the barcode, at the correct aspect, at the given
     * width, in dps.
     */
    fun measureHeightNeededForBarcode(
        text: String,
        type: BarcodeViewModelInterface.BarcodeType,
        width: Float
    ): Float
}
