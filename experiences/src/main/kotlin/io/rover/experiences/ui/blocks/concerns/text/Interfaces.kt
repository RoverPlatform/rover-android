package io.rover.experiences.ui.blocks.concerns.text

import android.text.Spanned
import io.rover.experiences.ui.blocks.concerns.layout.Measurable
import io.rover.experiences.ui.blocks.text.TextBlockViewModel
import io.rover.core.ui.concerns.BindableView
import io.rover.core.ui.concerns.BindableViewModel

interface ViewTextInterface: BindableView<TextViewModelInterface>

/**
 * View Model for block content that contains rich text content (decorated with strong, italic, and
 * underline HTML tags).
 */
interface TextViewModelInterface : Measurable, BindableViewModel {
    val text: String

    val singleLine: Boolean

    /**
     * Should the view configure the Android text view with a vertically centering gravity?
     */
    val centerVertically: Boolean

    val fontAppearance: FontAppearance

    fun boldRelativeToBlockWeight(): Font
}

/**
 * Transform a Rover HTML-decorated rich text string (as seen in Text blocks).
 *
 * This logic is kept outside of the [TextBlockViewModel] because it has runtime Android
 * dependencies.
 */
interface RichTextToSpannedTransformer {
    fun transform(string: String, boldRelativeToBlockWeight: Font): Spanned
}