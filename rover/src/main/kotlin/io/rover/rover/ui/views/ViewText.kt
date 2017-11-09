package io.rover.rover.ui.views

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView
import io.rover.rover.ui.RichTextToSpannedTransformer
import io.rover.rover.ui.viewmodels.TextBlockViewModelInterface

/**
 * Mixin that binds a text block view model to the relevant parts of a [TextView].
 */
class ViewText(
    private val textView: TextView,
    private val textToSpannedTransformer: RichTextToSpannedTransformer
): ViewTextInterface {
    init {
        textView.setLineSpacing(0f, 1.0f)
        // we can disable the built-in font padding because we already take font height padding into
        // account in our height measurement.  If this were left on, an inappropriate gap would be
        // left at the top of the text and ironically push the descenders off the bottom (don't
        // worry, the ascenders do not appear to be clipped either).
        textView.includeFontPadding = false
    }

    override var textViewModel: TextBlockViewModelInterface? = null
        set(viewModel) {
            if(viewModel != null) {
                // TODO: this may be a fair bit of compute at bind-time.  But not sure where to put
                // memoized android-specific stuff (the Spanned below) because the ViewModel is
                // off-limits for Android stuff

                val spanned = textToSpannedTransformer.transform(
                    viewModel.text,
                    viewModel.boldRelativeToBlockWeight()
                )

                textView.text = spanned

                textView.gravity = when(viewModel.fontAppearance.align) {
                    Paint.Align.RIGHT -> Gravity.END
                    Paint.Align.LEFT -> Gravity.START
                    Paint.Align.CENTER -> Gravity.CENTER_HORIZONTAL
                }

                textView.textSize = viewModel.fontAppearance.fontSize.toFloat()

                textView.setTextColor(viewModel.fontAppearance.color)

                textView.typeface = Typeface.create(
                    viewModel.fontAppearance.font.fontFamily, viewModel.fontAppearance.font.fontStyle
                )
            }
        }


}

