package io.rover.rover.ui.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.graphics.drawable.shapes.Shape
import android.util.DisplayMetrics
import android.view.View
import io.rover.rover.ui.types.dpAsPx
import io.rover.rover.ui.viewmodels.BackgroundViewModelInterface
import io.rover.rover.ui.viewmodels.BorderViewModelInterface


class ViewBorder(
    private val view: LayoutableView<*>
): ViewBorderInterface {
    // State:

    private var configuration: RenderingConfiguration? = null
    private var size: Pair<Int, Int>? = null

    init {
        val displayMetrics = view.resources.displayMetrics

        view.registerOnSizeChangedCallback { width, height, _, _ ->
            size = Pair(width, height)
            configureIfPossible()
        }

        // register callbacks with the View to get into the canvas rendering chain.
        view.registerAfterDraw { canvas ->
            val viewModel = borderViewModel
            val configuration = this.configuration

            if(viewModel != null && configuration != null) {
                canvas.drawRoundRect(
                    configuration.borderRect,
                    viewModel.borderRadius.dpAsPx(displayMetrics).toFloat(),
                    viewModel.borderRadius.dpAsPx(displayMetrics).toFloat(),
                    configuration.borderPaint
                )

                // TODO I still think this operation will be slow, because it will have to upload
                // our mask texture to the GPU every time :(

                if(configuration.roundedCornersMask != null) {
                    canvas.drawBitmap(
                        configuration.roundedCornersMask,
                        0f,
                        0f,
                        Paint(
                        ).apply {
                            // as far as the Porter-Duff alpha transfer is concerned, the "destination"
                            // is the view contents rendered by the rest of the Block implementation,
                            // and the source is our Mask paint (this drawRoundRect() operation).
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                        }
                    )
                }

                // whoops! drawRoundRect only operates on the actual pixels addressed by its
                // internal path, the exact opposite from what I want.  I have little choice but
                // to use an offscreen mask bitmap.


            }
        }
    }



    private fun configureIfPossible() {
        val viewModel = borderViewModel
        val size = this.size

        configuration = if(viewModel != null && size != null) {
            val displayMetrics = view.resources.displayMetrics

            val (width, height) = size

            val borderWidthPx = viewModel.borderWidth.dpAsPx(displayMetrics).toFloat()
            val borderInset = borderWidthPx / 2
            val borderRect = RectF(
                borderInset, borderInset, width.toFloat() - borderInset, height.toFloat() - borderInset
            )

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = viewModel.borderColor
                strokeWidth = borderWidthPx
                style = Paint.Style.STROKE
            }

            val maskBitmap = if (viewModel.borderRadius != 0) {
                val clipRect = RectF(
                    0f, 0f, width.toFloat(), height.toFloat()
                )

                val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
                val maskCanvas = Canvas(maskBitmap)

                maskCanvas.drawRoundRect(
                    clipRect,
                    viewModel.borderRadius.dpAsPx(displayMetrics).toFloat(),
                    viewModel.borderRadius.dpAsPx(displayMetrics).toFloat(),
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = Color.WHITE
                    }
                )
                maskBitmap

            } else {
                null
            }

            RenderingConfiguration(
                maskBitmap,
                borderRect,
                borderPaint
            )
        } else {
            null
        }

        // First side effects:
        // we're drawing translucence (specifically for the rounded corners) on the view's canvas
        // below.  In order to have Android's UI framework composite this view with the views below this one, we have to ask it
        // to render us into an offscreen framebuffer object.  If set back to software,
        if(configuration?.roundedCornersMask != null) {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else {
            view.setLayerType(View.LAYER_TYPE_NONE, null)
        }

        // Second side effect:
        // View must redraw after reconfiguration.
        view.invalidate()
    }

    override var borderViewModel: BorderViewModelInterface? = null
        set(viewModel) {
            field = viewModel
            if (viewModel != null) {
                field = viewModel

                configureIfPossible()
                // enable rounded corners code path and render out the alpha mask if needed
                // I need to process this in response to two events: view model change, or sizing change.


                // TODO: plan as follows:
                // ViewModels for backgrounds, borders, and images, and a
                // View mixin to go with it.  This avoids the FrameLayout method that the
                // stackoverflow snippet uses.  My base View(s) will then have a super thin
                // layer (perhaps itself a sort of mixin) for injecting things into the rendering
                // chaim with the mixin would then use. This means that ImageBlockView, powered by
                // ImageView, would be able to use this directly without being wrapped by anything.
                // And naturally, for when there’s no radius, that mixin will be able to just skip
                // drawing in the mask roundRect.

                // viewModel is stored, so just invalidate the view
                // to force a redraw.
            }
        }

    data class RenderingConfiguration(
        var roundedCornersMask: Bitmap? = null,
        var borderRect: RectF,
        var borderPaint: Paint
    )
}
