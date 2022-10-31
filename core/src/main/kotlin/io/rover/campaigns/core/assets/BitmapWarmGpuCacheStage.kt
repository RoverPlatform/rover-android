package io.rover.campaigns.core.assets

import android.graphics.Bitmap
import java.net.URL

/**
 * Ensure the bitmap asset is uploaded to the GPU.
 *
 * This is effectively the first layer of cache.
 */
class BitmapWarmGpuCacheStage(
    private val nextStage: SynchronousPipelineStage<URL, Bitmap>
) : SynchronousPipelineStage<URL, Bitmap> {
    override fun request(input: URL): PipelineStageResult<Bitmap> {
        return nextStage.request(input).apply {
            // Ask Android to upload the bitmap into GPU memory.  Notice that we're able to do this
            // in this context of our background thread pool, which means we can entirely avoid
            // expensive texture uploads happening in the hot-path of rendering frames on the UI
            // thread. This is also an LRU akin to our InMemoryBitmapCacheStage stage; but in this
            // case Android's framework maintains the LRU itself.
            when (this) {
                is PipelineStageResult.Successful -> this.output.prepareToDraw()
                is PipelineStageResult.Failed -> this.reason
            }
        }
    }
}
