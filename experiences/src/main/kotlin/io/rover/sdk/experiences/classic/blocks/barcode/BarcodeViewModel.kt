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

import io.rover.sdk.core.data.domain.Barcode
import io.rover.sdk.core.data.domain.BarcodeFormat
import io.rover.sdk.experiences.classic.RectF
import io.rover.sdk.experiences.services.MeasurementService

/**
 * Barcode display view model.
 */
internal class BarcodeViewModel(
    private val barcode: Barcode,
    private val measurementService: MeasurementService
) : BarcodeViewModelInterface {
    override val barcodeType: BarcodeViewModelInterface.BarcodeType
        get() = when (barcode.format) {
            BarcodeFormat.AztecCode -> BarcodeViewModelInterface.BarcodeType.Aztec
            BarcodeFormat.Code128 -> BarcodeViewModelInterface.BarcodeType.Code128
            BarcodeFormat.Pdf417 -> BarcodeViewModelInterface.BarcodeType.PDF417
            BarcodeFormat.QrCode -> BarcodeViewModelInterface.BarcodeType.QrCode
        }

    override val barcodeValue: String
        get() = barcode.text

    override fun intrinsicHeight(bounds: RectF): Float {
        return measurementService.measureHeightNeededForBarcode(
            barcode.text,
            barcodeType,
            bounds.width()
        )
    }
}
