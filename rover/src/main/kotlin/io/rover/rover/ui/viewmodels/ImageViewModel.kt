package io.rover.rover.ui.viewmodels

import android.graphics.Bitmap
import android.graphics.RectF
import io.rover.rover.core.domain.ImageBlock
import io.rover.rover.core.logging.log
import io.rover.rover.services.assets.AssetService
import io.rover.rover.services.network.NetworkResult
import io.rover.rover.services.network.NetworkTask

class ImageViewModel(
    private val block: ImageBlock,
    private val assetService: AssetService
) : ImageViewModelInterface {

    override fun requestImage(callback: (Bitmap) -> Unit): NetworkTask? {
        val uri = block.image?.url

        return if (uri != null) {
            log.v("There is an image to retrieve.  Starting.")
            // these are always URLs (HTTP/HTTPS), not open-ended URIs, so:
            val url = uri.toURL()

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
}
