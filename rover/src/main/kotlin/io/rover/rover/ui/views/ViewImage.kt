package io.rover.rover.ui.views

import android.support.v7.widget.AppCompatImageView
import android.view.View
import android.widget.ImageView
import io.rover.rover.core.logging.log
import io.rover.rover.platform.whenNotNull
import io.rover.rover.services.network.NetworkTask
import io.rover.rover.ui.viewmodels.ImageBlockViewModelInterface
import io.rover.rover.ui.types.PixelSize

/**
 * Mixin that binds an image block view model to the relevant parts of an [ImageView].
 */
class ViewImage(
    private val imageView: AppCompatImageView
) : ViewImageInterface {
    // State:
    private var runningTask: NetworkTask? = null

    private val dimensionCallbacks: MutableSet<DimensionCallback> = mutableSetOf()
    private var width: Int = 0
    private var height: Int = 0

    init {
        imageView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewDetachedFromWindow(v: View?) {
                runningTask?.cancel()
            }

            override fun onViewAttachedToWindow(v: View?) {}
        })
        imageView.scaleType = ImageView.ScaleType.FIT_XY

        // in order to know our realized width and height we need to listen for layout events.
        width = imageView.width
        height = imageView.height

        imageView.addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            width = right - left
            height = bottom - top
            dimensionCallbacks.forEach { it.invoke(width, height) }
            dimensionCallbacks.clear()
        }
    }

    /**
     * If dimensions are ready (or when they become ready), execute the given callback.
     */
    private fun whenDimensionsReady(callback: DimensionCallback) {
        if(width == 0 && height == 0) {
            // dimensions aren't ready. wait.
            log.v("Dimensions aren't ready.  Waiting.")
            dimensionCallbacks.add(callback)
        } else {
            log.v("Dimensions are already ready.  Firing immediately.")
            callback(width, height)
        }
    }

    override var imageBlockViewModel: ImageBlockViewModelInterface? = null
        set(viewModel) {
            if (viewModel != null) {
                // if there's already a running image fetch, cancel it before starting another.
                runningTask?.cancel()
                // and also clear any waiting layout callbacks
                dimensionCallbacks.clear()

                // we need to know the laid out dimensions of the view in order to ask the view
                // model for an optimized version of the image suited to the view's size.  So, we'll
                // install a View Tree Observer (VTO) to get notified that the layout has been
                // completed.

                whenDimensionsReady { width, height ->
                    runningTask = viewModel.requestImage(
                        PixelSize(width, height),
                        imageView.resources.displayMetrics
                    ) { bitmap ->
                        imageView.setImageBitmap(bitmap)
                    }.apply { this.whenNotNull { it.resume() } }
                }
            }
        }
}

typealias DimensionCallback = (width: Int, height: Int) -> Unit
