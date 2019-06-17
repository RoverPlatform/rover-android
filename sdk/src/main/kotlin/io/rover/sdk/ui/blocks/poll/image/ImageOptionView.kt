package io.rover.sdk.ui.blocks.poll.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import io.rover.sdk.data.domain.ImagePollBlockOptionStyle
import io.rover.sdk.data.mapToFont
import io.rover.sdk.platform.create
import io.rover.sdk.platform.imageView
import io.rover.sdk.platform.setupLayoutParams
import io.rover.sdk.platform.textView
import io.rover.sdk.ui.asAndroidColor
import io.rover.sdk.ui.blocks.poll.RoundRect
import io.rover.sdk.ui.dpAsPx

/**
 * Custom view that is "externally bound", the "bind" methods on this class are externally called
 * as opposed to other views which subscribe to a ViewModel. This means that this view and the views
 * that interact with it operate in a more MVP type approach than the other MVVM-esque views.
 */
internal class ImageOptionView(context: Context?) : LinearLayout(context) {
    private val optionTextView = textView {
        ellipsize = TextUtils.TruncateAt.END
        maxLines = 1
        gravity = Gravity.CENTER
        setupLayoutParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = 40f.dpAsPx(this.resources.displayMetrics)
        )
    }

    private val optionImageView = imageView { }

    private var roundRect: RoundRect? = null

    private var borderPaint = Paint()

    init {
        orientation = VERTICAL

        addView(optionImageView)
        addView(optionTextView)
    }

    fun bindOptionView(option: String, optionStyle: ImagePollBlockOptionStyle) {
        optionTextView.run {
            text = option
            textSize = optionStyle.font.size.toFloat()
            setTextColor(optionStyle.color.asAndroidColor())
            val font = optionStyle.font.weight.mapToFont()
            typeface = Typeface.create(font.fontFamily, font.fontStyle)
        }
    }

    fun bindOptionImageSize(imageLength: Int) {
        optionImageView.run {
            scaleType = ImageView.ScaleType.FIT_XY
            layoutParams = LayoutParams(imageLength, imageLength)
        }
    }

    fun bindOptionImage(bitmap: Bitmap) {
        optionImageView.run {
            setImageBitmap(bitmap)
        }
    }

    fun initializeOptionViewLayout(optionStyle: ImagePollBlockOptionStyle) {
        gravity = Gravity.CENTER_VERTICAL
        alpha = optionStyle.opacity.toFloat()
        setBackgroundColor(Color.TRANSPARENT)

        val borderRadius = optionStyle.border.radius.dpAsPx(resources.displayMetrics).toFloat()
        val borderStrokeWidth = optionStyle.border.width.dpAsPx(resources.displayMetrics).toFloat()

        borderPaint = Paint().create(
            optionStyle.border.color.asAndroidColor(),
            Paint.Style.STROKE,
            borderStrokeWidth
        )

        val halfStrokeWidth = borderStrokeWidth / 2
        val rect = RectF(halfStrokeWidth, halfStrokeWidth,
            width.toFloat() - halfStrokeWidth,
            height.toFloat() - halfStrokeWidth
        )
        roundRect = RoundRect(rect, borderRadius, halfStrokeWidth)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val halfStrokeWidth = roundRect?.halfBorderStrokeWidth ?: 0f
        roundRect = roundRect?.copy(
            rectF = RectF(
                halfStrokeWidth,
                halfStrokeWidth,
                width.toFloat() - (halfStrokeWidth),
                height.toFloat() - halfStrokeWidth
            )
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        roundRect?.let { roundRect ->
            roundRect.rectF.let {
                if (roundRect.halfBorderStrokeWidth != 0f) {
                    canvas.drawRoundRect(
                        it,
                        roundRect.borderRadius,
                        roundRect.borderRadius,
                        borderPaint)
                }
            }
        }
    }

    override fun draw(canvas: Canvas?) {
        roundRect?.let { roundRect ->
            roundRect.rectF.let {
                canvas?.clipPath(Path().apply {
                    addRoundRect(it, roundRect.borderRadius, roundRect.borderRadius, Path.Direction.CW)
                })
            }
        }
        super.draw(canvas)
    }
}