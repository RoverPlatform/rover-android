package io.rover.rover.ui.viewmodels

import android.graphics.Bitmap
import io.rover.rover.core.domain.Background
import io.rover.rover.core.logging.log
import io.rover.rover.services.assets.AssetService
import io.rover.rover.services.network.NetworkResult
import io.rover.rover.services.network.NetworkTask
import io.rover.rover.ui.views.asAndroidColor

class BackgroundViewModel(
    val background: Background,
    private val assetService: AssetService
) : BackgroundViewModelInterface {
    override val backgroundColor: Int
        get() = background.backgroundColor.asAndroidColor()

    override fun requestBackgroundImage(callback: (Bitmap) -> Unit): NetworkTask? {
        val uri = background.backgroundImage?.url
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
}
