package io.rover.sdk.platform

import android.graphics.Paint
import android.support.v7.widget.AppCompatButton
import android.support.v7.widget.AppCompatTextView
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import io.rover.sdk.ui.blocks.concerns.background.BackgroundColorDrawableWrapper
import io.rover.sdk.ui.blocks.poll.OptionView

internal fun ViewGroup.textView(text: String, builder: AppCompatTextView.() -> Unit): AppCompatTextView {
    return AppCompatTextView(context).apply {
        builder()
        this.text = text
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

internal fun View.setBackgroundWithoutPaddingChange(backgroundDrawable: BackgroundColorDrawableWrapper) {
    val paddingLeft = paddingLeft
    val paddingTop = paddingTop
    val paddingRight = paddingRight
    val paddingBottom = paddingBottom
    background = backgroundDrawable
    setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
}

internal fun View.setupLayoutParams(width: Int = 0, height: Int = 0, leftPadding: Int = 0,
                            topPadding: Int = 0, rightPadding: Int = 0, bottomPadding: Int = 0, leftMargin: Int = 0, topMargin: Int = 0, rightMargin: Int = 0 ,
                            bottomMargin: Int = 0) {
    this.layoutParams = ViewGroup.MarginLayoutParams(width, height).apply {
        setPadding(leftPadding, topPadding, rightPadding, bottomPadding)
        setMargins(leftMargin, topMargin, rightMargin, bottomMargin)
    }
}

internal fun View.setupRelativeLayoutParams(width: Int = 0, height: Int = 0, leftPadding: Int = 0,
                                    topPadding: Int = 0, rightPadding: Int = 0, bottomPadding: Int = 0, leftMargin: Int = 0, topMargin: Int = 0, rightMargin: Int = 0 ,
                                    bottomMargin: Int = 0, rulesBuilder: (RelativeLayout.LayoutParams.() -> Unit) = {}) {
    this.layoutParams = RelativeLayout.LayoutParams(width, height).apply {
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
    }
}

