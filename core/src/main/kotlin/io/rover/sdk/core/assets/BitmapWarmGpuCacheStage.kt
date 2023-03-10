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

package io.rover.sdk.core.assets

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
