/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.experiences.classic.blocks.poll

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import io.rover.sdk.core.data.domain.Border
import io.rover.sdk.experiences.classic.asAndroidColor
import io.rover.sdk.experiences.classic.dpAsPx
import io.rover.sdk.experiences.platform.create

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

        borderRoundRect = RoundRect(
            rectF = RectF(
                halfBorderWidth,
                halfBorderWidth,
                width.toFloat() - halfBorderWidth,
                height.toFloat() - halfBorderWidth
            ),
            borderRadius = compensatedBorderRadius,
            borderStrokeWidth = borderStrokeWidth
        )

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
