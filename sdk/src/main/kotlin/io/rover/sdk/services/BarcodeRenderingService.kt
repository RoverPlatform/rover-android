package io.rover.sdk.services

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import io.rover.sdk.platform.toAndroidBitmap
import io.rover.shaded.zxing.com.google.zxing.BarcodeFormat
import io.rover.shaded.zxing.com.google.zxing.EncodeHintType
import io.rover.shaded.zxing.com.google.zxing.MultiFormatWriter
import io.rover.shaded.zxing.com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

internal class BarcodeRenderingService {
    enum class Format {
        Pdf417, Code128, Aztec, QrCode
    }

    /**
     * Measure how much height a given bit of Unicode text will require if rendered as a barcode in
     * the specified format, and then meant to be scaled to fit the given width.
     *
     * @param format specifies what format of barcode should be used.
     *
     * Note that what length and sort of text is valid depends on the Barcode format.
     *
     * Returns the height needed to accommodate the barcode, at the correct aspect, at the given
     * width, in points.
     */
    fun measureHeightNeededForBarcode(
        text: String,
        format: Format,
        width: Float
    ): Float {
        val renderedBitmap = renderBarcode(text, format)
        val aspectRatio = renderedBitmap.width / renderedBitmap.height.toFloat()
        return (width / aspectRatio)
    }

    /**
     * Render the given string as a Barcode in the given format, pixel exact (not scaled).
     *
     * Note that what length and sort of text is valid depends on the Barcode format.
     */
    fun renderBarcode(
        text: String,
        format: Format
    ): Bitmap {
        return MultiFormatWriter().encode(
            text,
            when (format) {
                Format.Pdf417 -> BarcodeFormat.PDF_417
                Format.Code128 -> BarcodeFormat.CODE_128
                Format.Aztec -> BarcodeFormat.AZTEC
                Format.QrCode -> BarcodeFormat.QR_CODE
            },
            // we want the minimum size, pixel exact.  we'll scale it later in the view layer.
            0,
            0,
            when (format) {
                Format.Pdf417 -> hashMapOf<EncodeHintType, Any>(
                    // We'll furnish our own margin (see contributedPadding).  Some -- but not all --
                    // of the barcode types look for this margin parameter and if they don't
                    // find it include their own (pretty massive) margin.
                    Pair(EncodeHintType.MARGIN, 2),
                    Pair(EncodeHintType.ERROR_CORRECTION, 2),
                    Pair(EncodeHintType.PDF417_COMPACTION, "AUTO")
                )
                Format.Code128 -> hashMapOf<EncodeHintType, Any>(
                    // We cannot use this to obtain a margin around the outside of a Code 128
                    // barcode, because it is rendered as 1D and therefore margin will only appear
                    // on the horizontal sides.  So, we'll post-process the bitmap to add it
                    // instead, in addition to extending it out vertically to be 2 dimensions
                    // instead of 1.
                    Pair(EncodeHintType.MARGIN, 0)
                )
                Format.Aztec -> hashMapOf<EncodeHintType, Any>(
                    Pair(EncodeHintType.MARGIN, 1),
                    Pair(EncodeHintType.AZTEC_LAYERS, 0),
                    Pair(EncodeHintType.ERROR_CORRECTION, 33)
                )
                Format.QrCode -> hashMapOf<EncodeHintType, Any>(
                    Pair(EncodeHintType.MARGIN, 1),
                    Pair(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                )
            }
        ).toAndroidBitmap().let { bitmap ->
            when (format) {
                Format.Code128 -> {
                    // post process to transform the barcode into 2D and add a 2px border around it to be consistent
                    // with the other formats, and also add margin on all sides.

                    val margin = 1
                    val fixedHeight = 32
                    val newHeight = fixedHeight + margin * 2
                    val newWidth = bitmap.width + margin * 2

                    val newBitmap = Bitmap.createBitmap(newWidth, newHeight, bitmap.config)

                    val canvas = Canvas(newBitmap)

                    canvas.drawColor(Color.WHITE)

                    val destRect = RectF(
                        margin.toFloat(),
                        margin.toFloat(),
                        (margin + bitmap.width).toFloat(),
                        (margin + fixedHeight).toFloat()
                    )
                    canvas.drawBitmap(
                        bitmap,
                        Matrix().apply {
                            setRectToRect(
                                RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                                destRect,
                                Matrix.ScaleToFit.FILL
                            )
                        },
                        null // by default the Paint will not have FILTER_BITMAP_FLAG on, which is what we want.
                    )
                    newBitmap
                }
                Format.Aztec -> {
                    // post-process to add our own margin on all sides:
                    val margin = 2

                    val newHeight = bitmap.height + margin * 2
                    val newWidth = bitmap.width + margin * 2

                    val newBitmap = Bitmap.createBitmap(newWidth, newHeight, bitmap.config)

                    val canvas = Canvas(newBitmap)

                    canvas.drawColor(Color.WHITE)

                    val destRect = RectF(
                        margin.toFloat(),
                        margin.toFloat(),
                        (margin + bitmap.width).toFloat(),
                        (margin + bitmap.height).toFloat()
                    )

                    canvas.drawBitmap(
                        bitmap,
                        Rect(
                            0, 0, bitmap.width, bitmap.height
                        ),
                        destRect,
                        null
                    )

                    newBitmap
                }
                else -> bitmap
            }
        }
    }
}
