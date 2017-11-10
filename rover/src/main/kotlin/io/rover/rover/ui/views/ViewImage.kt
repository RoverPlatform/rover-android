package io.rover.rover.ui.views

import android.support.v7.widget.AppCompatImageView
import android.view.View
import android.widget.ImageView
import io.rover.rover.platform.whenNotNull
import io.rover.rover.services.network.NetworkTask
import io.rover.rover.ui.viewmodels.ImageBlockViewModelInterface

/**
 * Mixin that binds an image block view model to the relevant parts of an [ImageView].
 */
class ViewImage(
    private val imageView: AppCompatImageView
) : ViewImageInterface {
    var runningTask: NetworkTask? = null

    init {
        imageView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewDetachedFromWindow(v: View?) {
                runningTask?.cancel()
            }

            override fun onViewAttachedToWindow(v: View?) {}
        })
        imageView.scaleType = ImageView.ScaleType.FIT_XY
    }

    override var imageBlockViewModel: ImageBlockViewModelInterface? = null
        set(viewModel) {
            if (viewModel != null) {
                // if there's already a running image fetch, cancel it before starting another.
                runningTask?.cancel()

                runningTask = viewModel.requestImage { bitmap ->
                    imageView.setImageBitmap(bitmap)
                }.apply { this.whenNotNull { it.resume() } }
            }
        }
}
