package io.rover.experiences.ui.blocks.image

import android.support.v7.widget.AppCompatImageView
import android.widget.ImageView
import io.rover.core.streams.androidLifecycleDispose
import io.rover.core.streams.subscribe
import io.rover.core.ui.concerns.ViewModelBinding
import io.rover.core.ui.concerns.BindableView

/**
 * Mixin that binds an image block view model to the relevant parts of an [ImageView].
 */
class ViewImage(
    private val imageView: AppCompatImageView
) : ViewImageInterface {
    private val shortAnimationDuration = imageView.resources.getInteger(
        android.R.integer.config_shortAnimTime
    )

    init {
        imageView.scaleType = ImageView.ScaleType.FIT_XY
    }

    override var viewModel: BindableView.Binding<ImageBlockViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->
        if (binding != null) {
            val measuredSize = binding.measuredSize ?: throw RuntimeException("ViewImage may only be used with a view model binding including a measured size (ie. used within a Rover screen layout).")

            imageView.alpha = 0f

            // we need to know the laid out dimensions of the view in order to ask the view
            // model for an optimized version of the image suited to the view's size.

            binding.viewModel.imageUpdates.androidLifecycleDispose(
                imageView
            ).subscribe({ imageUpdate ->
                imageView.setImageBitmap(imageUpdate.bitmap)
                if(imageUpdate.fadeIn) {
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
