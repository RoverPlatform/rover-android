package io.rover.rover.services.assets

import android.graphics.Bitmap
import android.util.LruCache
import io.rover.rover.core.logging.log
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
    // tuneable parameter (but this has poor DX and is still little better than a fudge-factor). Can
    // we dynamically tune?

    // TODO: at the very least expose the 8-factor as a tuneable.

    /**
     * Maximum memory available to this process in kilobytes.
     */
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()

    init {
        log.v("There are $maxMemory KiB available to the in memory bitmap cache.")
    }

    /**
     * The LRU cache itself, set up to use one eighth of the total memory allowed for this process,
     * as recommended by https://developer.android.com/topic/performance/graphics/cache-bitmap.html.
     */
    private val lruCache = object : LruCache<URL, Bitmap>(maxMemory / 8) {
        override fun sizeOf(key: URL, value: Bitmap): Int {
            return value.byteCount / 1024
        }

//        override fun entryRemoved(evicted: Boolean, key: URL?, oldValue: Bitmap, newValue: Bitmap?) {
//            // release the heap memory containing the bitmap and also any video memory
//            // TODO: this can break if the image is bigger than the total cache (which should *probably never happen?) because it will be evicted while still in use.  Maybe not strictly necessary to recycle on our own?
//            log.v("Value getting recycled.")
//            oldValue.recycle()
//        }

        override fun create(key: URL): Bitmap {
            this@InMemoryBitmapCacheStage.log.v("Image not available in cache, faulting to next layer.")
            return faultTo.request(key)
        }
    }

    override fun request(input: URL): Bitmap {
        return lruCache[input]
    }
}
