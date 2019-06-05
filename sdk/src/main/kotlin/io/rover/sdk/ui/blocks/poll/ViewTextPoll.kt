package io.rover.sdk.ui.blocks.poll

import android.widget.LinearLayout
import io.rover.sdk.data.domain.TextPollBlock
import io.rover.sdk.platform.button
import io.rover.sdk.platform.setDimens
import io.rover.sdk.platform.textView
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.ViewModelBinding
import io.rover.sdk.ui.dpAsPx

internal class ViewTextPoll(override val view: LinearLayout) : ViewTextPollInterface {
    override var viewModelBinding: MeasuredBindableView.Binding<TextPollViewModelInterface>? by ViewModelBinding { binding, _ ->
        binding?.viewModel?.textPollBlock?.let {
            createViews(it)
        }
    }

    private fun createViews(textPollBlock: TextPollBlock) {
        val question = view.textView {
            text = textPollBlock.question
        }

        view.addView(question)

        val optionStyleHeight = textPollBlock.optionStyle.height.dpAsPx(view.resources.displayMetrics)
        val optionMarginHeight =
            textPollBlock.optionStyle.verticalSpacing.dpAsPx(view.resources.displayMetrics)

        textPollBlock.options.forEachIndexed { index, option ->
            val button = view.button {
                id = index
                background = null
                text = option
                setDimens(
                    width = LinearLayout.LayoutParams.MATCH_PARENT,
                    height = optionStyleHeight,
                    topMargin = optionMarginHeight
                )
            }

            view.addView(button)
        }
    }
}

internal interface ViewTextPollInterface : MeasuredBindableView<TextPollViewModelInterface>