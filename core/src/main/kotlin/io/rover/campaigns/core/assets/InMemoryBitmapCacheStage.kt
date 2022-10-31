package io.rover.campaigns.core.assets

import android.graphics.Bitmap
import android.util.LruCache
import io.rover.campaigns.core.logging.log
import java.net.URL

/**
 * This pipeline stage contains an in-memory cache of [Bitmap]s.  If a bitmap is not cached for
 * the given URL, it will fault to the subsequent stage.
 *
 * This is the second layer of cache.
 */
class InMemoryBitmapCacheStage(
    private val faultTo: SynchronousPipelineStage<URL, Bitmap>
) : SynchronousPipelineStage<URL, Bitmap> {

    // TODO: how do we tune size? Google recommends tuning by creating a formula that uses a static
    // factor (here, 8) suited to your app and then a dynamic factor for the device’s available
    // per-app memory.  For us, surrounding app is also a dynamic factor. Existing SDK just has a
    // tunable parameter (but this has poor DX and is still little better than a fudge-factor). Can
    // we dynamically tune?

    // TODO: at the very least expose the 8-factor as a tunable.

    /**
     * Maximum memory available to this process in kilobytes.
     */
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()

    init {
        log.v("There are $maxMemory KiB available to the memory bitmap cache.")
    }

    /**
     * The LRU cache itself, set up to use one eighth of the total memory allowed for this process,
     * as recommended by https://developer.android.com/topic/performance/graphics/cache-bitmap.html.
     *
     * Holds onto a reference to the bitmap, but lets the reference go once evicted.  Then it's up
     * to the GC to free the bitmap and therefore recycle it.
     */
    private val lruCache = object : LruCache<URL, Bitmap>(maxMemory / 8) {
        override fun sizeOf(key: URL, value: Bitmap): Int = value.byteCount / 1024

        override fun create(key: URL): Bitmap {
            this@InMemoryBitmapCacheStage.log.v("Image not available in cache, faulting to next layer.")
            val created = faultTo.request(key)
            return when (created) {
                is PipelineStageResult.Failed -> throw UnableToCreateEntryException(created.reason)
                is PipelineStageResult.Successful -> created.output
            }
        }
    }

    override fun request(input: URL): PipelineStageResult<Bitmap> {
        return try {
            val value: Bitmap? = lruCache[input]
            if(value == null) {
                // this means that the LRUCache was not able to create a new value for any number
                // of possible reasons. If so, fault directly to next layer and skip the cache.
                faultTo.request(input)
            } else {
                PipelineStageResult.Successful(lruCache[input])
            }
        } catch (e: UnableToCreateEntryException) {
            PipelineStageResult.Failed(e.reason)
        } catch (e: IllegalStateException) {
            PipelineStageResult.Failed(e)
        }
    }

    private class UnableToCreateEntryException(val reason: Throwable) : Exception(reason)
}
