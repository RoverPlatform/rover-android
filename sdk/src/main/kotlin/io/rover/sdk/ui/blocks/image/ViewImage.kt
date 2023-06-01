package io.rover.sdk.ui.blocks.image

import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import android.widget.ImageView
import io.rover.sdk.streams.androidLifecycleDispose
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.concerns.ViewModelBinding
import io.rover.sdk.ui.concerns.MeasuredBindableView

/**
 * Mixin that binds an image block view model to the relevant parts of an [ImageView].
 */
internal class ViewImage(
    private val imageView: AppCompatImageView
) : ViewImageInterface {
    private val shortAnimationDuration = imageView.resources.getInteger(
        android.R.integer.config_shortAnimTime
    )

    init {
        imageView.scaleType = ImageView.ScaleType.FIT_XY
    }

    override var viewModelBinding: MeasuredBindableView.Binding<ImageBlockViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->
        if (binding != null) {
            val measuredSize = binding.measuredSize ?: throw RuntimeException("ViewImage may only be used with a view model binding including a measured size (ie. used within a Rover screen layout).")

            with(imageView) {

                alpha = 0f
                val isAccessible = binding.viewModel.isClickable || !binding.viewModel.isDecorative
                // We do not want to set the image as focusable, because this causes undesirable
                // behaviour for users not using TalkBack/a11y technology: they'd have to click
                // the image twice if it has a click action on it.  importantForAccessibility
                // is all we need to have the image be announced by TalkBack.
                importantForAccessibility = if (isAccessible) {
                    contentDescription = binding.viewModel.accessibilityLabel
                    View.IMPORTANT_FOR_ACCESSIBILITY_YES
                } else {
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }

            }

            // we need to know the laid out dimensions of the view in order to ask the view
            // model for an optimized version of the image suited to the view's size.

            binding.viewModel.imageUpdates.androidLifecycleDispose(
                imageView
            ).subscribe({ imageUpdate ->
                imageView.setImageBitmap(imageUpdate.bitmap)
                if (imageUpdate.fadeIn) {
                    imageView.animate()
                        // TODO: we should not have to peek the surrounding ImageBlockViewModel interface to discover opacity.
                        .alpha(binding.viewModel.opacity)
                        .setDuration(shortAnimationDuration.toLong())
                        .start()
                } else {
                    imageView.alpha = binding.viewModel.opacity
                }
            }, { error -> throw(error) }, { subscriptionCallback(it) })

            binding.viewModel.informDimensions(measuredSize)
        }
    }
}
