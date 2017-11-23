package io.rover.rover.services.assets

import android.graphics.Bitmap
import android.util.DisplayMetrics
import io.rover.rover.core.domain.Background
import io.rover.rover.core.domain.ImageBlock
import io.rover.rover.services.network.NetworkResult
import io.rover.rover.services.network.NetworkTask
import io.rover.rover.ui.types.PixelSize
import io.rover.rover.ui.types.dpAsPx
import io.rover.rover.ui.viewmodels.BackgroundImageConfiguration
import java.net.URI
import java.net.URL

/**
 * A pipeline step that does I/O operations of (fetch, cache, etc.) an asset.
 *
 * Stages will synchronously block the thread while waiting for I/O.
 *
 * Stages should not block to do computation-type work, however; the asset pipeline is
 * run on a thread pool optimized for I/O multiplexing and not computation.
 */
interface SynchronousPipelineStage<in TInput, out TOutput> {
    fun request(input: TInput): TOutput
}

interface AssetService {
    /**
     * Retrieve the needed photo, from caches if possible.
     *
     * [completionHandler] will be called on app's main UI thread.
     *
     * TODO: retry logic will not exist on the consumer-side of this method, so rather than
     * NetworkResult, another result<->error optional type should be used instead.
     */
    fun getImageByUrl(
        url: URL,
        completionHandler: ((NetworkResult<Bitmap>) -> Unit)
    ): NetworkTask
}

interface ImageOptimizationServiceInterface {
    /**
     * Take a given background and return a URI and image configuration that may be used to display
     * it efficiently.  It may perform transforms on the URI and background image configuration to
     * cut down retrieving and decoding an unnecessary larger image than needed for the context.
     *
     * Note that this does not actually perform any sort optimization operation locally.
     */
    fun optimizeImageBackground(
        background: Background,
        targetViewPixelSize: PixelSize,
        displayMetrics: DisplayMetrics
    ): OptimizedImage?

    /**
     * Take a given image block and return the URI.
     *
     * (No configuration is given here because
     */
    fun optimizeImageBlock(
        block: ImageBlock,
        targetViewPixelSize: PixelSize,
        displayMetrics: DisplayMetrics
    ): URI?
}

/**
 * A retrieval URI and configuration needed for displaying an image.
 */
data class OptimizedImage(
    /**
     * The (potentially) modified URI.
     */
    val uri: URI,

    /**
     * The (potentially) modified image display configuration.
     */
    val imageConfiguration: BackgroundImageConfiguration
)
