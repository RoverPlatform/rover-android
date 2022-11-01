package io.rover.experiences.ui.blocks.concerns.background

import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.view.Gravity
import android.view.View
import io.rover.experiences.platform.DrawableWrapper
import io.rover.core.streams.androidLifecycleDispose
import io.rover.core.streams.subscribe
import io.rover.experiences.ui.BackgroundImageConfiguration
import io.rover.experiences.ui.concerns.MeasuredBindableView
import io.rover.experiences.ui.concerns.ViewModelBinding

internal class ViewBackground(
    override val view: View
) : ViewBackgroundInterface {
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
                    val paddingLeft = view.paddingLeft
                    val paddingTop = view.paddingTop
                    val paddingRight = view.paddingRight
                    val paddingBottom = view.paddingBottom
                    view.background = bitmap.createBackgroundDrawable(
                        view,
                        viewModel.backgroundColor,
                        fadeIn,
                        backgroundImageConfiguration
                    )
                    view.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
                }, { error -> throw error }, { subscriptionCallback(it) })

            binding.viewModel.informDimensions(
                binding.measuredSize ?: throw RuntimeException(
                    "ViewBackground may only be used with a view model binding including a measured size (ie. used within a Rover screen layout)."
                )
            )
        }
    }
}

internal fun Bitmap.createBackgroundDrawable(
    view: View,
    backgroundColor: Int,
    fadeIn: Boolean,
    backgroundImageConfiguration: BackgroundImageConfiguration
): BackgroundColorDrawableWrapper {
    // now construct/compose drawables for the given configuration and bitmap.

    // note that this will only have an effect in tiled mode (which is exactly
    // where we need it), since we always scale to the insets otherwise.
    density = backgroundImageConfiguration.imageNativeDensity

    val bitmapDrawable =
        BitmapDrawable(
            view.resources,
            this
        ).apply {
            this.gravity = Gravity.FILL
            if (backgroundImageConfiguration.tileMode != null) {
                tileModeX = backgroundImageConfiguration.tileMode
                tileModeY = backgroundImageConfiguration.tileMode
            }
        }

    val backgroundDrawable = BackgroundColorDrawableWrapper(
        backgroundColor,
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
            duration = view.resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
            start()
        }
    } else {
        backgroundDrawable.alpha = 255
    }

    return backgroundDrawable
}

internal class BackgroundColorDrawableWrapper(
    private val backgroundColor: Int,
    private val drawableOnTopOfColor: Drawable
) : DrawableWrapper(drawableOnTopOfColor) {

    override fun draw(canvas: Canvas) {
        canvas.drawColor(backgroundColor)
        drawableOnTopOfColor.draw(canvas)
    }
}
