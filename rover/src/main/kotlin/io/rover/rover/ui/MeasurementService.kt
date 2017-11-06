package io.rover.rover.ui

import io.rover.rover.ui.types.Font
import io.rover.rover.ui.types.FontAppearance

interface MeasurementService {

    /**
     * Measure how much height a given bit of Unicode [richText] (with optional HTML tags such as
     * strong, italic, and underline) will require if soft wrapped to the given [width] and
     * [fontAppearance] ultimately meant to be displayed using an Android View.
     *
     * [boldFontAppearance] provides any font size or font-family modifications that should be
     * applied to bold text ( with no changes to colour or alignment).  This allows for nuanced
     * control of bold span styling.
     */
    fun measureHeightNeededForRichText(
        richText: String,
        fontAppearance: FontAppearance,
        boldFontAppearance: Font,
        width: Float
    ): Float
}
