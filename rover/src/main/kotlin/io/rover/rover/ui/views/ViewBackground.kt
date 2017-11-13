package io.rover.rover.ui.views

import android.graphics.drawable.BitmapDrawable
import android.view.View
import io.rover.rover.core.logging.log
import io.rover.rover.platform.whenNotNull
import io.rover.rover.services.network.NetworkTask
import io.rover.rover.ui.viewmodels.BackgroundViewModelInterface

class ViewBackground(
    private val view: View
) : ViewBackgroundInterface {
    var runningTask: NetworkTask? = null

    init {
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewDetachedFromWindow(v: View?) {
                runningTask?.cancel()
            }

            override fun onViewAttachedToWindow(v: View?) {}
        })
    }

    override var backgroundViewModel: BackgroundViewModelInterface? = null
        set(viewModel) {
            if (viewModel != null) {
                view.setBackgroundColor(viewModel.backgroundColor)

                runningTask?.cancel()

                runningTask = viewModel.requestBackgroundImage { bitmap ->
                    // now construct drawable from the params.
                    // TODO: attempt to configure the drawable's scale/transforms appropriately. the background viewmodel will obviously be deeply involved with this task.
                    val bitmapDrawable = BitmapDrawable(
                        view.resources,
                        bitmap
                    )
                    log.v("Background retrieved, setting.")
                    view.background = bitmapDrawable
                }.apply { this.whenNotNull { it.resume() } }
            }
        }
}


// Do I want multiple Drawables for each or do I rather want a minimalist Drawable that then can be
// used to expose my own drawable logic.
