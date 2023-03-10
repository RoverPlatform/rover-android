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

package io.rover.sdk.experiences.classic.blocks.barcode

import android.R
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import io.rover.sdk.core.Rover
import io.rover.sdk.experiences.RoverExperiencesClassic
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView
import io.rover.sdk.experiences.classic.concerns.ViewModelBinding
import io.rover.sdk.experiences.services.BarcodeRenderingService

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
            barcodeView.setImageResource(R.color.transparent)
        }
    }

    private val barcodeRenderingService = Rover.shared.resolve(RoverExperiencesClassic::class.java)?.barcodeRenderingService
        ?: throw RuntimeException("Rover Experience view layer not usable until Rover.initialize has been called (with ExperiencesAssembler included).")
}
