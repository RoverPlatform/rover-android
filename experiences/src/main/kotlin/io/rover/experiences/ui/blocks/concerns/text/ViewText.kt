package io.rover.experiences.ui.blocks.concerns.text

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.view.Gravity
import android.widget.TextView
import io.rover.core.ui.concerns.BindableView
import io.rover.core.ui.concerns.ViewModelBinding

/**
 * Mixin that binds a text block view model to the relevant parts of a [TextView].
 */
class ViewText(
    private val textView: TextView,
    private val textToSpannedTransformer: RichTextToSpannedTransformer
) : ViewTextInterface {
    init {
        textView.setLineSpacing(0f, 1.0f)
        // we can disable the built-in font padding because we already take font height padding into
        // account in our height measurement.  If this were left on, an inappropriate gap would be
        // left at the top of the text and ironically push the descenders off the bottom (don't
        // worry, the ascenders do not appear to be clipped either).
        textView.includeFontPadding = false

        // Experiences app does not wrap text on text blocks.  This seems particularly
        // important for short, tight blocks.
        // Unfortunately, we cannot disable it on Android older than 23.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            textView.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }
    }

    override var viewModel: BindableView.Binding<TextViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->
        if (binding != null) {
            // TODO: this may be a fair bit of compute at bind-time.  But not sure where to put
            // memoized android-specific stuff (the Spanned below) because the ViewModel is
            // off-limits for Android stuff

            val spanned = textToSpannedTransformer.transform(
                binding.viewModel.text,
                binding.viewModel.boldRelativeToBlockWeight()
            )

            textView.text = spanned

            textView.gravity = when (binding.viewModel.fontAppearance.align) {
                Paint.Align.RIGHT -> Gravity.END
                Paint.Align.LEFT -> Gravity.START
                Paint.Align.CENTER -> Gravity.CENTER_HORIZONTAL
            } or if (binding.viewModel.centerVertically) Gravity.CENTER_VERTICAL else 0

            textView.textSize = binding.viewModel.fontAppearance.fontSize.toFloat()

            textView.setTextColor(binding.viewModel.fontAppearance.color)

            textView.typeface = Typeface.create(
                binding.viewModel.fontAppearance.font.fontFamily, binding.viewModel.fontAppearance.font.fontStyle
            )

            textView.setSingleLine(binding.viewModel.singleLine)
        }
    }
}
