package io.rover.rover.ui

import io.rover.rover.core.domain.FontWeight
import io.rover.rover.ui.viewmodels.FontFace

interface MeasurementService {

    /**
     * Measure how much height a given bit of Unicode [richText] (with optional HTML tags such as
     * strong, italic, and underline) will require if soft wrapped to the given [width] and
     * [fontFace] ultimately meant to be displayed using an Android View.
     */
    fun measureHeightNeededForRichText(
        richText: String,
        fontFace: FontFace,
        width: Float
    ): Float
}
