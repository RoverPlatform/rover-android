package io.rover.rover.ui.views

import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.support.v7.graphics.drawable.DrawableWrapper
import android.view.Gravity
import android.view.View
import io.rover.rover.core.logging.log
import io.rover.rover.platform.whenNotNull
import io.rover.rover.services.network.NetworkTask
import io.rover.rover.ui.types.PixelSize
import io.rover.rover.ui.viewmodels.BackgroundViewModelInterface

class ViewBackground(
    private val view: View
) : ViewBackgroundInterface {
    // State:
    var runningTask: NetworkTask? = null

    private val dimensionCallbacks: MutableSet<DimensionCallback> = mutableSetOf()
    private var width: Int = 0
    private var height: Int = 0

    init {
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewDetachedFromWindow(v: View?) {
                runningTask?.cancel()
            }

            override fun onViewAttachedToWindow(v: View?) {}
        })

        // in order to know our realized width and height we need to listen for layout events.
        width = view.width
        height = view.height

        view.addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
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

    override var backgroundViewModel: BackgroundViewModelInterface? = null
        set(viewModel) {
            if (viewModel != null) {
                view.setBackgroundColor(viewModel.backgroundColor)

                // if there's already a running image fetch, cancel it before starting another.
                runningTask?.cancel()

                whenDimensionsReady { width, height ->
                    runningTask = viewModel.requestBackgroundImage(
                        PixelSize(width, height),
                        view.resources.displayMetrics
                    ) { bitmap, backgroundImageConfiguration ->
                        // now construct/compose drawables for the given configuration and bitmap.

                        val bitmapDrawable =
                            BitmapDrawable(
                                view.resources,
                                bitmap
                            ).apply {
                                this.gravity = Gravity.FILL
                                if(backgroundImageConfiguration.tileMode != null) {
                                    tileModeX = backgroundImageConfiguration.tileMode
                                    tileModeY = backgroundImageConfiguration.tileMode
                                }
                            }

                        view.background =
                            BackgroundColorDrawableWrapper(
                                viewModel.backgroundColor,
                                InsetDrawable(
                                    bitmapDrawable,
                                    backgroundImageConfiguration.insets.left,
                                    backgroundImageConfiguration.insets.top,
                                    backgroundImageConfiguration.insets.right,
                                    backgroundImageConfiguration.insets.bottom
                                )
                            )
                    }.apply { this.whenNotNull { it.resume() } }
                }
            }
        }
}

class BackgroundColorDrawableWrapper(
    private val backgroundColor: Int,
    private val drawableOnTopOfColor: Drawable
): DrawableWrapper(drawableOnTopOfColor) {

    override fun draw(canvas: Canvas) {
        canvas.drawColor(backgroundColor)
        drawableOnTopOfColor.draw(canvas)
    }
}
