package io.rover.rover.ui.viewmodels

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import io.rover.rover.core.domain.ImageBlock
import io.rover.rover.core.logging.log
import io.rover.rover.services.assets.AssetService
import io.rover.rover.services.network.NetworkResult
import io.rover.rover.services.network.NetworkTask
import io.rover.rover.ui.types.PixelSize
import io.rover.rover.ui.types.dpAsPx
import java.net.URL

class ImageViewModel(
    private val block: ImageBlock,
    private val assetService: AssetService
) : ImageViewModelInterface {
    override fun requestImage(
        targetViewPixelSize: PixelSize,
        displayDensity: Float,
        callback: (Bitmap) -> Unit
    ): NetworkTask? {
        val uri = block.image?.url

        return if (uri != null) {
            log.v("There is an image to retrieve.  Starting.")
            val uriWithParameters = Uri.parse(uri.toString()).buildUpon().apply {
                imgixParameters(
                    targetViewPixelSize,
                    displayDensity
                ).forEach { (key, value) ->  appendQueryParameter(key, value) }
            }.build()
            val url = URL(uriWithParameters.toString())

            assetService.getImageByUrl(url) { result ->
                val y = when (result) {
                    is NetworkResult.Success -> callback(result.response)
                    is NetworkResult.Error -> {
                        // TODO perhaps attempt a retry? or should a lower layer attempt retry?
                        // concern should remain here if the experience UI should react or indicate
                        // an error somehow.
                        log.e("Problem retrieving image: ${result.throwable}")
                    }
                }
            }
        } else {
            // log.v("Null URI.  No image set.")
            null
        }
    }

    override fun intrinsicHeight(bounds: RectF): Float {
        val image = block.image

        return if (image == null) {
            // no image set means no height at all.
            0f
        } else {
            // get aspect ratio of image and use it to calculate the height needed to accommodate
            // the image at its correct aspect ratio given the width
            val heightToWidthRatio = image.height.toFloat() / image.width.toFloat()
            bounds.width() * heightToWidthRatio
        }
    }

    /**
     * We use a service called Imgix to do cloud-side transforms of our images. Here we're using it
     * for a scale down transform, if needed. On first usage the cloud service will execute the
     * transform and then cache the result, meaning that all other users on other devices viewing
     * the same image asset will get the previously processed bits.
     */
    private fun imgixParameters(
        targetViewPixelSize: PixelSize,
        displayDensity: Float
    ): Map<String, String> {
        return if(block.image != null) {
            // TODO: should any of this Imgix scaling logic be pulled out into a separate concern?

            val imageSizePixels = PixelSize(
                block.image.width,
                block.image.height
            )

            // Now take border width into account.
            val borderWidth = block.borderWidth.dpAsPx(displayDensity)
            val targetViewSizeWithoutBorderWidth = PixelSize(
                targetViewPixelSize.width - borderWidth,
                targetViewPixelSize.height - borderWidth
            )

            // if the ultimate image to be rendered on the screen is going to smaller (in terms of
            // pixel count) than the source, in terms of pixel count (relevant to data plan usage
            // for users), then we'll ask Imgix to execute the scale operation for us instead.
            // However, we will ask for an aspect-correct scale from Imgix because we'll end up
            // scaling up the larger dimension on our end, saving even more bytes.  Note that we
            // won't need to have View change the scaling mode from the FIT_XY mode we're already
            // using because no crop operation is going on here.
            val smallestSize = minOf(imageSizePixels, targetViewSizeWithoutBorderWidth)
            mapOf(
                Pair("w", smallestSize.width.toString()),
                Pair("h", smallestSize.height.toString())
            )
        } else {
            mapOf()
        }
    }
}
