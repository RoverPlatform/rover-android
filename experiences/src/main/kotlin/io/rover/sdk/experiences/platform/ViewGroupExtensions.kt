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

package io.rover.sdk.experiences.platform

import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import io.rover.sdk.experiences.classic.blocks.concerns.background.BackgroundColorDrawableWrapper
import io.rover.sdk.experiences.classic.blocks.poll.image.ImagePollOptionView
import io.rover.sdk.experiences.classic.blocks.poll.image.VotingIndicatorBar
import io.rover.sdk.experiences.classic.blocks.poll.text.TextOptionView
import io.rover.sdk.experiences.classic.blocks.poll.text.TextPollProgressBar

// Extension functions to reduce the amount of boilerplate when programmatically creating
// and modifying views

internal fun ViewGroup.textView(builder: AppCompatTextView.() -> Unit): AppCompatTextView {
    return AppCompatTextView(context).apply {
        builder()
    }
}

internal fun ViewGroup.imageView(builder: AppCompatImageView.() -> Unit): AppCompatImageView {
    return AppCompatImageView(context).apply {
        builder()
    }
}

internal fun ViewGroup.button(builder: AppCompatButton.() -> Unit): AppCompatButton {
    return AppCompatButton(context).apply {
        builder()
    }
}

internal fun ViewGroup.optionView(builder: TextOptionView.() -> Unit): TextOptionView {
    return TextOptionView(context).apply {
        builder()
    }
}

internal fun ViewGroup.imageOptionView(builder: ImagePollOptionView.() -> Unit): ImagePollOptionView {
    return ImagePollOptionView(context).apply {
        builder()
    }
}

internal fun ViewGroup.votingIndicatorBar(builder: VotingIndicatorBar.() -> Unit): VotingIndicatorBar {
    return VotingIndicatorBar(context).apply {
        builder()
    }
}

internal fun ViewGroup.textPollProgressBar(builder: TextPollProgressBar.() -> Unit): TextPollProgressBar {
    return TextPollProgressBar(context).apply {
        builder()
    }
}

internal fun ViewGroup.relativeLayout(builder: RelativeLayout.() -> Unit): RelativeLayout {
    return RelativeLayout(context).apply {
        builder()
    }
}

internal fun ViewGroup.linearLayout(builder: LinearLayout.() -> Unit): LinearLayout {
    return LinearLayout(context).apply {
        builder()
    }
}

internal fun ViewGroup.view(builder: View.() -> Unit): View {
    return View(context).apply {
        builder()
    }
}

internal fun View.setBackgroundWithoutPaddingChange(backgroundDrawable: BackgroundColorDrawableWrapper) {
    val paddingLeft = paddingLeft
    val paddingTop = paddingTop
    val paddingRight = paddingRight
    val paddingBottom = paddingBottom
    background = backgroundDrawable
    setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
}

internal fun View.setupLayoutParams(
    width: Int = 0,
    height: Int = 0,
    leftPadding: Int = 0,
    topPadding: Int = 0,
    rightPadding: Int = 0,
    bottomPadding: Int = 0,
    leftMargin: Int = 0,
    topMargin: Int = 0,
    rightMargin: Int = 0,
    bottomMargin: Int = 0
) {
    layoutParams = ViewGroup.MarginLayoutParams(width, height).apply {
        setPadding(leftPadding, topPadding, rightPadding, bottomPadding)
        setMargins(leftMargin, topMargin, rightMargin, bottomMargin)
    }
}

internal fun View.setupLinearLayoutParams(
    width: Int = 0,
    height: Int = 0,
    leftPadding: Int = 0,
    topPadding: Int = 0,
    rightPadding: Int = 0,
    bottomPadding: Int = 0,
    leftMargin: Int = 0,
    topMargin: Int = 0,
    rightMargin: Int = 0,
    bottomMargin: Int = 0
) {
    layoutParams = LinearLayout.LayoutParams(width, height).apply {
        setPadding(leftPadding, topPadding, rightPadding, bottomPadding)
        setMargins(leftMargin, topMargin, rightMargin, bottomMargin)
    }
}

internal fun View.setupRelativeLayoutParams(
    width: Int = 0,
    height: Int = 0,
    leftPadding: Int = 0,
    topPadding: Int = 0,
    rightPadding: Int = 0,
    bottomPadding: Int = 0,
    leftMargin: Int = 0,
    topMargin: Int = 0,
    rightMargin: Int = 0,
    bottomMargin: Int = 0,
    rulesBuilder: (RelativeLayout.LayoutParams.() -> Unit) = {}
) {
    layoutParams = RelativeLayout.LayoutParams(width, height).apply {
        setPadding(leftPadding, topPadding, rightPadding, bottomPadding)
        setMargins(leftMargin, topMargin, rightMargin, bottomMargin)
        rulesBuilder()
    }
}

internal fun ViewGroup.addView(viewBuilder: ViewGroup.() -> View) {
    addView(viewBuilder())
}

internal fun Paint.create(paintColor: Int, paintStyle: Paint.Style): Paint {
    return this.apply {
        color = paintColor
        style = paintStyle
    }
}

internal fun Paint.create(paintColor: Int, paintStyle: Paint.Style, paintStrokeWidth: Float): Paint {
    return this.apply {
        color = paintColor
        style = paintStyle
        strokeWidth = paintStrokeWidth
        isAntiAlias = true
    }
}
