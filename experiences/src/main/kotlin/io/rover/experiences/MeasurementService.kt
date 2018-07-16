package io.rover.experiences

import io.rover.experiences.ui.blocks.concerns.text.Font
import io.rover.experiences.ui.blocks.concerns.text.FontAppearance
import io.rover.experiences.ui.blocks.barcode.BarcodeViewModelInterface

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
