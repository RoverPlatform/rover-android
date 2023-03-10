/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.experiences.classic.blocks.concerns.text

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.view.Gravity
import android.widget.TextView
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView
import io.rover.sdk.experiences.classic.concerns.ViewModelBinding

/**
 * Mixin that binds a text block view model to the relevant parts of a [TextView].
 */
internal class ViewText(
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

        // This is set to prevent the wrong line spacing being used on android 10+ when StyleSpans are applied
        if (Build.VERSION.SDK_INT >= 29) {
            textView.isFallbackLineSpacing = false
        }

        // Experiences app does not wrap text on text blocks.  This seems particularly
        // important for short, tight blocks.
        // Unfortunately, we cannot disable it on Android older than 23.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            textView.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }
    }

    override var viewModelBinding: MeasuredBindableView.Binding<TextViewModelInterface>? by ViewModelBinding { binding, _ ->
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
                binding.viewModel.fontAppearance.font.fontFamily,
                binding.viewModel.fontAppearance.font.fontStyle
            )

            textView.setSingleLine(binding.viewModel.singleLine)
        }
    }
}
