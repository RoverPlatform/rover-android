package io.rover.core.assets

import android.graphics.Bitmap
import io.rover.core.data.NetworkResult
import org.reactivestreams.Publisher
import java.net.URL

/**
 * A pipeline step that does I/O operations of (fetch, cache, etc.) an asset.
 *
 * Stages will synchronously block the thread while waiting for I/O.
 *
 * Stages should not block to do computation-type work, however; the asset pipeline is
 * run on a thread pool optimized for I/O multiplexing and not computation.
 */
interface SynchronousPipelineStage<in TInput, TOutput> {
    fun request(input: TInput): PipelineStageResult<TOutput>
}

sealed class PipelineStageResult<TOutput> {
    class Successful<TOutput>(val output: TOutput) : PipelineStageResult<TOutput>()
    class Failed<TOutput>(val reason: Throwable) : PipelineStageResult<TOutput>()
}

interface AssetService {
    /**
     * Subscribe to any updates for the image at URL, fully decoded and ready for display.
     *
     * It will complete after successfully yielding once.  However, it will not attempt
     *
     * The Publisher will yield on the app's main UI thread.
     */
    fun imageByUrl(
        url: URL
    ): Publisher<Bitmap>

    /**
     * Use this to request and wait for a single update to the given image asset by URI.  Use this
     * is lieu of [imageByUrl] if you need to subscribe to a single update.
     */
    fun getImageByUrl(
        url: URL
    ): Publisher<NetworkResult<Bitmap>>

    // TODO: the aggregation behaviour will go in the Asset Service.

    /**
     * Request a fetch.  Be sure you are subscribed to [imageByUrl] to receive the updates.
     */
    fun tryFetch(
        url: URL
    )
}
