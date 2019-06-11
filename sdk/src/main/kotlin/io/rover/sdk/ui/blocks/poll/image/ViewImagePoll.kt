package io.rover.sdk.ui.blocks.poll.image

import android.graphics.Typeface
import android.support.v4.view.ViewCompat
import android.support.v7.widget.AppCompatTextView
import android.view.ViewGroup
import android.widget.LinearLayout
import io.rover.sdk.data.domain.ImagePollBlock
import io.rover.sdk.logging.log
import io.rover.sdk.platform.addView
import io.rover.sdk.platform.imageOptionView
import io.rover.sdk.platform.mapToFont
import io.rover.sdk.platform.setupLayoutParams
import io.rover.sdk.platform.textView
import io.rover.sdk.streams.androidLifecycleDispose
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.MeasuredSize
import io.rover.sdk.ui.concerns.ViewModelBinding
import io.rover.sdk.ui.dpAsPx
import io.rover.sdk.ui.pxAsDp

internal class ViewImagePoll(override val view: LinearLayout) :
    ViewImagePollInterface {
    private val questionView = view.textView {
        setupLayoutParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    private var optionViews: List<ImageOptionView>? = null

    init {
        view.addView {
            questionView
        }
    }

    override var viewModelBinding: MeasuredBindableView.Binding<ImagePollViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->

        binding?.viewModel?.let {
            val width = binding.measuredSize?.width ?: 0f

            val verticalSpacing = it.imagePollBlock.optionStyle.verticalSpacing

            val imageLength =
                (width.dpAsPx(view.resources.displayMetrics) - verticalSpacing.dpAsPx(view.resources.displayMetrics)) / 2

            bindQuestion(it.imagePollBlock)

            setupOptionViews(it, imageLength)

            binding.viewModel.multiImageUpdates.androidLifecycleDispose(this.view).subscribe({ imageList ->
                optionViews?.forEachIndexed { index, imageOptionView ->
                    imageOptionView.bindOptionImage(imageList[index].bitmap)
                }
            }, { error -> log.w("Problem fetching poll images: $error, ignoring.") }, { subscription ->  subscriptionCallback(subscription) })

            binding.viewModel.informImagePollOptionDimensions(
                MeasuredSize(
                    imageLength.pxAsDp(view.resources.displayMetrics),
                    imageLength.pxAsDp(view.resources.displayMetrics),
                    view.resources.displayMetrics.density
                )
            )
        }
    }

    private fun bindQuestion(imagePollBlock: ImagePollBlock) {
        questionView.run {
            text = imagePollBlock.question
            gravity = imagePollBlock.questionStyle.textAlignment.convertToGravity()
            textSize = imagePollBlock.questionStyle.font.size.toFloat()
            setTextColor(imagePollBlock.questionStyle.color.asAndroidColor())
            val font = imagePollBlock.questionStyle.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }

    private fun setupOptionViews(viewModel: ImagePollViewModelInterface, imageLength: Int) {
        optionViews = createOptionViews(viewModel.imagePollBlock, imageLength)

        when {
            optionViews?.size == 2 -> {
                val row = LinearLayout(view.context)
                view.addView(row)


                optionViews?.forEach { optionView -> row.addView(optionView) }
            }
            optionViews?.size == 4 -> {
                val row1 = LinearLayout(view.context)
                val row2 = LinearLayout(view.context)
                view.addView(row1)
                view.addView(row2)

                optionViews?.forEachIndexed { index, optionView ->
                    if (index < 2) row1.addView(optionView) else row2.addView(optionView)
                }
            }
        }
    }

    private fun createOptionViews(imagePollBlock: ImagePollBlock, imageLength: Int): List<ImageOptionView> {
        val optionTextHeight = 40f.dpAsPx(view.resources.displayMetrics)
        val horizontalSpacing =
            imagePollBlock.optionStyle.horizontalSpacing.dpAsPx(view.resources.displayMetrics)
        val verticalSpacing =
            imagePollBlock.optionStyle.verticalSpacing.dpAsPx(view.resources.displayMetrics)

        return imagePollBlock.options.mapIndexed { index, option ->
            view.imageOptionView {
                initializeOptionViewLayout(imagePollBlock.optionStyle)
                bindOptionView(option.text, imagePollBlock.optionStyle)
                bindOptionImageSize(imageLength)
                setupLayoutParams(
                    width = imageLength,
                    height = imageLength + optionTextHeight,
                    rightMargin = if (index == 0 || index == 2) horizontalSpacing else 0,
                    topMargin = verticalSpacing
                )
            }
        }
    }
}

internal interface ViewImagePollInterface : MeasuredBindableView<ImagePollViewModelInterface>