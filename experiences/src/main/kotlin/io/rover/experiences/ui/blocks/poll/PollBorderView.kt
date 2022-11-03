package io.rover.experiences.ui.blocks.poll

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import io.rover.core.data.domain.Border
import io.rover.experiences.platform.create
import io.rover.experiences.ui.asAndroidColor
import io.rover.experiences.ui.dpAsPx

internal class PollBorderView(context: Context?) : View(context) {
    private var borderPaint = Paint()
    private var borderRoundRect: RoundRect? = null

    var border: Border? = null
    set(value) {
        field = value
        border?.let {
            setBorderRoundRect(it)
        }
    }

    private fun setBorderRoundRect(border: Border) {
        val halfBorderWidth = border.width.dpAsPx(resources.displayMetrics).toFloat() / 2
        val borderRadius = border.radius.dpAsPx(resources.displayMetrics).toFloat()
        val borderStrokeWidth = border.width.dpAsPx(resources.displayMetrics).toFloat()

        // This is in order to maintain the same distance between the middle of the view border and outer edge of the clipped
        // parent view so that the border extends all the way to the edge of the clipped view.
        val compensatedBorderRadius = if (borderRadius - halfBorderWidth > 0) borderRadius - halfBorderWidth else 0f

        borderRoundRect = RoundRect(rectF = RectF(halfBorderWidth, halfBorderWidth,
            width.toFloat() - halfBorderWidth,
            height.toFloat() - halfBorderWidth),
            borderRadius = compensatedBorderRadius,
            borderStrokeWidth = borderStrokeWidth)

        borderPaint = Paint().create(
            border.color.asAndroidColor(),
            Paint.Style.STROKE,
            border.width.dpAsPx(resources.displayMetrics).toFloat()
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        border?.let { setBorderRoundRect(it) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (borderRoundRect?.borderStrokeWidth != 0f) {
            borderRoundRect?.let {
                canvas.drawRoundRect(it.rectF, it.borderRadius, it.borderRadius, borderPaint)
            }
        }
    }
}
