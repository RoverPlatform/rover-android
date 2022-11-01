package io.rover.experiences.ui.blocks.barcode

import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.widget.AppCompatImageView
import android.widget.ImageView
import io.rover.experiences.services.BarcodeRenderingService
import io.rover.experiences.RoverExperiences
import io.rover.experiences.ui.concerns.MeasuredBindableView
import io.rover.experiences.ui.concerns.ViewModelBinding

/**
 * Mixin that binds a barcode view model to an [AppCompatImageView] by rendering the barcodes and
 * displaying them in the image view.
 */
internal class ViewBarcode(
    private val barcodeView: AppCompatImageView
) : ViewBarcodeInterface {
    init {
        // Using stretch fit because  we've ensured that the image will scale aspect-correct
        // ([barcodeView], the target imageview, will always have the correct aspect ratio because
        // auto-height will always on), and we also are using integer scaling to ensure a sharp
        // scale of the pixels.  While we could use FIT_CENTER, FIT_XY will avoid the barcode
        // leaving any unexpected gaps around the outside in case of lack of agreement.
        barcodeView.scaleType = ImageView.ScaleType.FIT_XY
    }

    override var viewModelBinding: MeasuredBindableView.Binding<BarcodeViewModelInterface>? by ViewModelBinding { binding, _ ->
        // much).
        val viewModel = binding?.viewModel
        if (viewModel != null) {
            val bitmap = barcodeRenderingService.renderBarcode(
                viewModel.barcodeValue,
                when (viewModel.barcodeType) {
                    BarcodeViewModelInterface.BarcodeType.Aztec -> BarcodeRenderingService.Format.Aztec
                    BarcodeViewModelInterface.BarcodeType.Code128 -> BarcodeRenderingService.Format.Code128
                    BarcodeViewModelInterface.BarcodeType.PDF417 -> BarcodeRenderingService.Format.Pdf417
                    BarcodeViewModelInterface.BarcodeType.QrCode -> BarcodeRenderingService.Format.QrCode
                }
            )

            val nearestScaleDrawable = BitmapDrawable(
                barcodeView.resources,
                bitmap
            )

            // The ZXing library appropriately renders the barcodes at their smallest
            // pixel-exact size.  Thus, we want non-anti-aliased (ie., simple nearest-neighbor
            // scaling instead of the default bilinear filtering) of the image so as to have
            // sharp pixel-art for the barcodes, otherwise we'd get a seriously blurry mess.
            nearestScaleDrawable.isFilterBitmap = false

            barcodeView.setImageDrawable(
                nearestScaleDrawable
            )
        } else {
            barcodeView.setImageResource(android.R.color.transparent)
        }
    }

    private val barcodeRenderingService = RoverExperiences.shared?.barcodeRenderingService
        ?: throw RuntimeException("Rover not usable until Rover.initialize has been called.")
}
