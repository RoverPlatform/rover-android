package io.rover.experiences.ui.blocks.concerns.text

import android.text.Spanned
import io.rover.experiences.ui.blocks.concerns.layout.Measurable
import io.rover.experiences.ui.blocks.text.TextBlockViewModel
import io.rover.experiences.ui.concerns.MeasuredBindableView
import io.rover.experiences.ui.concerns.BindableViewModel

internal interface ViewTextInterface : MeasuredBindableView<TextViewModelInterface>

/**
 * View Model for block content that contains rich text content (decorated with strong, italic, and
 * underline HTML tags).
 */
internal interface TextViewModelInterface : Measurable, BindableViewModel {
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
internal interface RichTextToSpannedTransformer {
    fun transform(string: String, boldRelativeToBlockWeight: Font): Spanned
}