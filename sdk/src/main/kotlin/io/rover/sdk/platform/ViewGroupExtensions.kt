package io.rover.sdk.platform

import android.support.v7.widget.AppCompatButton
import android.support.v7.widget.AppCompatTextView
import android.view.ViewGroup
import android.widget.LinearLayout

fun LinearLayout.textView(builder: AppCompatTextView.() -> Unit): AppCompatTextView {
    return AppCompatTextView(context).apply {
        builder()
    }
}

fun LinearLayout.button(builder: AppCompatButton.() -> Unit): AppCompatButton {
    return AppCompatButton(context).apply {
        builder()
    }
}

fun AppCompatButton.setDimens(width: Int, height: Int, topMargin: Int) {
    layoutParams = ViewGroup.MarginLayoutParams(width, height).apply {
        this.topMargin = topMargin
    }
}