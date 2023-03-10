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

import android.text.Spanned
import io.rover.sdk.experiences.classic.blocks.concerns.layout.Measurable
import io.rover.sdk.experiences.classic.blocks.text.TextBlockViewModel
import io.rover.sdk.experiences.classic.concerns.BindableViewModel
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView

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
