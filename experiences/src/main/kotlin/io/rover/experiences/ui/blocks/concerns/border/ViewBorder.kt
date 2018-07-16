package io.rover.experiences.ui.blocks.concerns.border

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.View
import io.rover.core.platform.whenNotNull
import io.rover.core.ui.dpAsPx
import io.rover.experiences.ui.blocks.concerns.layout.PaddingContributor
import io.rover.experiences.ui.blocks.concerns.ViewCompositionInterface
import io.rover.experiences.ui.blocks.concerns.layout.Padding
import io.rover.core.ui.concerns.BindableView
import io.rover.core.ui.concerns.ViewModelBinding

class ViewBorder(
    override val view: View,
    viewComposition: ViewCompositionInterface
) : ViewBorderInterface, PaddingContributor {
    // State:
    private var configuration: MaskConfiguration? = null
    private var size: Pair<Int, Int>? = null

    override var viewModel: BindableView.Binding<BorderViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->
        renderRoundedCornersMaskIfPossible(binding?.viewModel)
    }

    init {
        val displayMetrics = view.resources.displayMetrics

        viewComposition.registerOnSizeChangedCallback { width, height, _, _ ->
            size = Pair(width, height)
            renderRoundedCornersMaskIfPossible(viewModel?.viewModel)
        }

        // register callbacks with the View to get into the canvas rendering chain.
        viewComposition.registerAfterDraw { canvas ->
            val viewModel = this.viewModel?.viewModel
            val configuration = this.configuration

            // canvas is potentially deflected because of a content scroll, particularly with web
            // views.  I don't want the border to scroll (or otherwise be transformed) with the
            // content, so I'll just reset the canvas matrix to an identity (no-op) matrix.
            canvas.matrix = Matrix()

            // have we discovered the view size and also been bound to a viewmodel?
            if (viewModel != null && configuration != null) {
                // draw the solid border, if needed:
                if (configuration.borderPaint != null) {
                    canvas.drawRoundRect(
                        configuration.borderRect,
                        viewModel.borderRadius.dpAsPx(displayMetrics).toFloat(),
                        viewModel.borderRadius.dpAsPx(displayMetrics).toFloat(),
                        configuration.borderPaint
                    )
                }

                // if there's a border radius set, then we draw our rendered alpha mask texture
                // that was rendered to an appropriate size (at configuration time) on top of the
                // rendered view.
                if (configuration.roundedCornersMask != null) {
                    // The first frame we run this, the mask texture (roundedCornersMask) will
                    // be uploaded to the GPU.
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
            }
        }
    }

    /**
     * Compute and memoize some state in this view mixin depending on the set view model and
     * the size of the view.  Idempotent; this method must be run whenever the view
     * model changes or the size of the view changes.
     *
     * This implements a particular method of achieving rounded corners on the content: computing
     * and caching an alpha mask than can be then quickly drawn from GPU memory onto the rendered
     * contents of the [View].
     *
     * Rationale of this selection of implementation: there were two other candidate methods for
     * achieving this effect: using [Canvas.clipRect] on the [Canvas] that the view contents
     * rendered on to clip out the corners, or, to directly render transparent corners onto the
     * canvas with [Canvas.drawRoundRect] and a PorterDuff filter.  The former method was slow and
     * did not anti-alias along the clip (important if view content such as an image would be flush
     * with the edges of the corners), and the latter method could not work at all because
     * Canvas.drawRoundRect would only touch those pixels the mask would directly apply to, thus
     * leaving the PorterDuff filter useless.
     */
    private fun renderRoundedCornersMaskIfPossible(viewModel: BorderViewModelInterface?) {
        val size = this.size

        configuration = if (viewModel != null && size != null) {
            val displayMetrics = view.resources.displayMetrics

            val (width, height) = size

            val borderWidthPx = viewModel.borderWidth.dpAsPx(displayMetrics).toFloat()
            val borderInset = borderWidthPx / 2
            val borderRect = RectF(
                borderInset, borderInset, width.toFloat() - borderInset, height.toFloat() - borderInset
            )

            val borderPaint = if (viewModel.borderWidth != 0) {
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = viewModel.borderColor
                    strokeWidth = borderWidthPx
                    style = Paint.Style.STROKE
                }
            } else {
                null
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

            MaskConfiguration(
                maskBitmap,
                borderRect,
                borderPaint
            )
        } else {
            null
        }

        // we're drawing translucence (specifically for the rounded corners) on the view's canvas
        // below.  In order to have Android's UI framework composite this view with the views
        // below this one, we have to ask it to render us into an offscreen framebuffer object
        // (aka have the view use a "hardware layer").
        if (configuration?.roundedCornersMask != null) {
            // view model is now displaying a view with rounded corners that needs the alpha mask,
            // so force to a hardware layer is explained above.
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else {
            // if this view model is not (or no longer) displaying a view with rounded corners
            // that would need the alpha mask, then switch back to default layer mode (probably
            // software).
            view.setLayerType(View.LAYER_TYPE_NONE, null)
        }

        // View must redraw after reconfiguration (ie the display list cache it may have produced
        // on prior renders is hereby invalidated).
        view.invalidate()
    }



    override val contributedPadding: Padding
        get() {
            return viewModel?.viewModel.whenNotNull {
                Padding(
                    it.borderWidth,
                    it.borderWidth,
                    it.borderWidth,
                    it.borderWidth
                )
            } ?: throw RuntimeException("ViewBorder must be bound to the view model before ViewBlock.") // not a great way to enforce this invariant, alas.
        }

    data class MaskConfiguration(
        var roundedCornersMask: Bitmap? = null,
        var borderRect: RectF,
        var borderPaint: Paint?
    )
}
