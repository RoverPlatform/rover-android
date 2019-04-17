package io.rover.sdk.ui.blocks.concerns.background

import android.animation.ObjectAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.view.Gravity
import android.view.View
import io.rover.sdk.streams.androidLifecycleDispose
import io.rover.sdk.streams.subscribe
import io.rover.sdk.platform.DrawableWrapper
import io.rover.sdk.ui.concerns.ViewModelBinding
import io.rover.sdk.ui.concerns.MeasuredBindableView

class ViewBackground(
    override val view: View
) : ViewBackgroundInterface {
    private val shortAnimationDuration = view.resources.getInteger(
        android.R.integer.config_shortAnimTime
    )

    override var viewModelBinding: MeasuredBindableView.Binding<BackgroundViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->
        view.background = null
        view.setBackgroundColor(Color.TRANSPARENT)

        val viewModel = binding?.viewModel

        if (viewModel != null) {
            view.background = null
            view.setBackgroundColor(viewModel.backgroundColor)

            binding
                .viewModel
                .backgroundUpdates
                .androidLifecycleDispose(view)
                .subscribe({ (bitmap, fadeIn, backgroundImageConfiguration) ->
                // now construct/compose drawables for the given configuration and bitmap.

                // note that this will only have an effect in tiled mode (which is exactly
                // where we need it), since we always scale to the insets otherwise.
                bitmap.density = backgroundImageConfiguration.imageNativeDensity

                val bitmapDrawable =
                    BitmapDrawable(
                        view.resources,
                        bitmap
                    ).apply {
                        this.gravity = Gravity.FILL
                        if (backgroundImageConfiguration.tileMode != null) {
                            tileModeX = backgroundImageConfiguration.tileMode
                            tileModeY = backgroundImageConfiguration.tileMode
                        }
                    }

                val backgroundDrawable = BackgroundColorDrawableWrapper(
                    viewModel.backgroundColor,
                    InsetDrawable(
                        bitmapDrawable,
                        backgroundImageConfiguration.insets.left,
                        backgroundImageConfiguration.insets.top,
                        backgroundImageConfiguration.insets.right,
                        backgroundImageConfiguration.insets.bottom
                    )
                )

                if (fadeIn) {
                    ObjectAnimator.ofInt(
                        backgroundDrawable, "alpha", 0, 255
                    ).apply {
                        duration = shortAnimationDuration.toLong()
                        start()
                    }
                } else {
                    backgroundDrawable.alpha = 255
                }

                view.background = backgroundDrawable
            }, { error -> throw error }, { subscriptionCallback(it) })

            binding.viewModel.informDimensions(binding.measuredSize ?: throw RuntimeException(
                "ViewBackground may only be used with a view model binding including a measured size (ie. used within a Rover screen layout)."
            ))
        }
    }
}

class BackgroundColorDrawableWrapper(
    private val backgroundColor: Int,
    private val drawableOnTopOfColor: Drawable
) : DrawableWrapper(drawableOnTopOfColor) {

    override fun draw(canvas: Canvas) {
        canvas.drawColor(backgroundColor)
        drawableOnTopOfColor.draw(canvas)
    }
}
