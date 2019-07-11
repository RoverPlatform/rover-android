package io.rover.sdk.ui.blocks.poll

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import io.rover.sdk.data.domain.Border
import io.rover.sdk.platform.create
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.dpAsPx

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

        borderRoundRect = RoundRect(rectF = RectF(halfBorderWidth, halfBorderWidth,
            width.toFloat() - halfBorderWidth,
            height.toFloat() - halfBorderWidth),
            borderRadius = border.radius.dpAsPx(resources.displayMetrics).toFloat(),
            borderStrokeWidth = border.width.dpAsPx(resources.displayMetrics).toFloat())

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


