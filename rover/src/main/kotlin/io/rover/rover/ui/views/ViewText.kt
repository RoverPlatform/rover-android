package io.rover.rover.ui.views

import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.widget.TextView
import io.rover.rover.platform.roverTextHtmlAsSpanned
import io.rover.rover.ui.viewmodels.TextBlockViewModelInterface

class ViewText(
    private val textView: TextView
): ViewTextInterface {
    init {
        textView.setLineSpacing(0f, 1.0f)
    }

    override var textViewModel: TextBlockViewModelInterface? = null
        set(viewModel) {
            if(viewModel != null) {
                // TODO: transform the spanned
                textView.text = viewModel.text.roverTextHtmlAsSpanned()

                textView.gravity = when(viewModel.fontFace.align) {
                    Paint.Align.RIGHT -> Gravity.END
                    Paint.Align.LEFT -> Gravity.START
                    Paint.Align.CENTER -> Gravity.CENTER_HORIZONTAL
                }

                textView.textSize = viewModel.fontFace.fontSize.toFloat()

                textView.setTextColor(viewModel.fontFace.color)

                textView.typeface = Typeface.create(
                    viewModel.fontFace.fontFamily, viewModel.fontFace.fontStyle
                )
            }
        }
}
