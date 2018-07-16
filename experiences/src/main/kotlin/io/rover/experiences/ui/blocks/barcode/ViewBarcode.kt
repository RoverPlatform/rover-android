package io.rover.experiences.ui.blocks.barcode

import android.R
import android.graphics.drawable.BitmapDrawable
import android.support.v7.widget.AppCompatImageView
import android.widget.ImageView
import io.rover.experiences.ui.blocks.concerns.layout.Padding
import io.rover.core.platform.toAndroidBitmap
import io.rover.core.ui.concerns.ViewModelBinding
import io.rover.experiences.ui.blocks.concerns.layout.PaddingContributor
import io.rover.core.ui.concerns.BindableView
import io.rover.shaded.zxing.com.google.zxing.BarcodeFormat
import io.rover.shaded.zxing.com.google.zxing.EncodeHintType
import io.rover.shaded.zxing.com.google.zxing.MultiFormatWriter

/**
 * Mixin that binds a barcode view model to an [AppCompatImageView] by rendering the barcodes
 * with ZXing.
 */
class ViewBarcode(
    private val barcodeView: AppCompatImageView
) : ViewBarcodeInterface, PaddingContributor {
    init {
        // Using stretch fit because (at least for auto-height) we've ensured that the image will
        // scale aspect-correct, and we also are using integer scaling to ensure a sharp scale of
        // the pixels.  Instead of FIT_CENTER, in the case of Code 128 (the only supported 1D
        // barcode) this allows us to use the GPU to scale the height of the 1px high barcode to fit
        // the view, saving a little bit of memory.
        barcodeView.scaleType = ImageView.ScaleType.FIT_XY
    }

    override var viewModel: BindableView.Binding<BarcodeViewModelInterface>? by ViewModelBinding { binding, _ ->
        // much).
        val viewModel = binding?.viewModel
        if (viewModel != null) {
            val bitmap = MultiFormatWriter().encode(
                viewModel.barcodeValue,
                when (viewModel.barcodeType) {
                    BarcodeViewModelInterface.BarcodeType.PDF417 -> BarcodeFormat.PDF_417
                    BarcodeViewModelInterface.BarcodeType.Code128 -> BarcodeFormat.CODE_128
                    BarcodeViewModelInterface.BarcodeType.Aztec -> BarcodeFormat.AZTEC
                    BarcodeViewModelInterface.BarcodeType.QrCode -> BarcodeFormat.QR_CODE
                },
                // we want the minimum size, pixel exact.  we'll scale it later.
                0,
                0,
                hashMapOf(
                    // I furnish my own margin (see contributedPadding).  Some -- but not all --
                    // of the barcode types look for this margin parameter and if they don't
                    // find it include their own (pretty massive) margin.
                    Pair(EncodeHintType.MARGIN, 0)
                )
            ).toAndroidBitmap()

            val nearestScaleDrawable = BitmapDrawable(
                barcodeView.resources,
                bitmap
            ).apply {
                // The ZXing library appropriately renders the barcodes at their smallest
                // pixel-exact size.  Thus, we want non-anti-aliased (ie., simple integer
                // scaling instead of the default bilinear filtering) of the image so as to have
                // sharp pixel-art for the barcodes, otherwise we'd get a seriously blurry mess.
                isFilterBitmap = false
            }

            barcodeView.setImageDrawable(
                nearestScaleDrawable
            )
        } else {
            barcodeView.setImageResource(R.color.transparent)
        }
    }

    override val contributedPadding: Padding
        get() = viewModel?.viewModel?.paddingDeflection ?: throw RuntimeException("ViewBarcode must be bound to the view model before ViewBlock.") // not a great way to enforce this invariant, alas.
}
