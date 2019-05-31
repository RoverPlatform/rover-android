package io.rover.sdk.platform

import android.support.v7.widget.AppCompatButton
import android.support.v7.widget.AppCompatTextView
import android.view.ViewGroup
import android.widget.LinearLayout
import io.rover.sdk.ui.blocks.poll.OptionView

internal fun LinearLayout.textView(builder: AppCompatTextView.() -> Unit): AppCompatTextView {
    return AppCompatTextView(context).apply {
        builder()
    }
}

internal fun LinearLayout.button(builder: AppCompatButton.() -> Unit): AppCompatButton {
    return AppCompatButton(context).apply {
        builder()
    }
}

internal fun LinearLayout.optionView(builder: OptionView.() -> Unit): OptionView {
    return OptionView(context).apply {
        builder()
    }
}

internal fun OptionView.setDimens(width: Int, height: Int, topMargin: Int, padding: Int) {
    layoutParams = ViewGroup.MarginLayoutParams(width, height).apply {
        this.topMargin = topMargin
        setPadding(padding, padding, padding, padding)
    }
}

internal fun AppCompatTextView.setDimens(width: Int, height: Int, topMargin: Int, padding: Int) {
    layoutParams = ViewGroup.MarginLayoutParams(width, height).apply {
        this.topMargin = topMargin
        setPadding(padding, padding, padding, padding)
    }
}