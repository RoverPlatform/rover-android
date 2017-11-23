package io.rover.rover.ui.viewmodels

import android.graphics.Bitmap
import android.util.DisplayMetrics
import io.rover.rover.core.domain.Background
import io.rover.rover.core.logging.log
import io.rover.rover.services.assets.AssetService
import io.rover.rover.services.assets.ImageOptimizationServiceInterface
import io.rover.rover.services.network.NetworkResult
import io.rover.rover.services.network.NetworkTask
import io.rover.rover.ui.types.PixelSize
import io.rover.rover.ui.views.asAndroidColor

class BackgroundViewModel(
    private val background: Background,
    private val assetService: AssetService,
    private val imageOptimizationService: ImageOptimizationServiceInterface
) : BackgroundViewModelInterface {
    override val backgroundColor: Int
        get() = background.backgroundColor.asAndroidColor()

    override fun requestBackgroundImage(
        targetViewPixelSize: PixelSize,
        displayMetrics: DisplayMetrics,
        callback: (Bitmap, BackgroundImageConfiguration) -> Unit
    ): NetworkTask? {
        val uri = background.backgroundImage?.url
        return if (uri != null) {
            val (urlToFetch, imageConfiguration) =
                imageOptimizationService.optimizeImageBackground(
                    background,
                    targetViewPixelSize,
                    displayMetrics
                ) ?: return null

            assetService.getImageByUrl(urlToFetch.toURL()) { result ->
                val y = when (result) {
                    is NetworkResult.Success -> {
                        callback(
                            result.response,
                            imageConfiguration
                        )
                    }
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
