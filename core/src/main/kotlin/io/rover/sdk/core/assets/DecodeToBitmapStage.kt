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
import android.graphics.BitmapFactory
import io.rover.sdk.core.logging.log
import java.io.BufferedInputStream
import java.net.URL

/**
 * This pipeline stage decodes an incoming stream into a bitmap. It implicitly supports (and
 * auto-detects) any image format that Android supports). We guarantee support for the image formats
 * used by Rover, specifically, GIF, JPEG and PNG.
 */
class DecodeToBitmapStage(
    private val priorStage: SynchronousPipelineStage<URL, BufferedInputStream>
) : SynchronousPipelineStage<URL, Bitmap> {
    override fun request(input: URL): PipelineStageResult<Bitmap> {
        val stream = priorStage.request(input)

        log.v("Decoding bitmap.")
        // Technically we should not be doing CPU-bound work in this context (which is being run on
        // an Executor tuned for multiplexing I/O and not for CPU work); see
        // SynchronousPipelineStage's documentation. If it proves problematic we can offload it to
        // another worker.
        return when (stream) {
            is PipelineStageResult.Successful -> {
                val result = PipelineStageResult.Successful(
                    BitmapFactory.decodeStream(
                        stream.output
                    )
                )
                // TODO do I need to close the stream myself, or will decodeStream() do it?
                stream.output.close()
                result
            }
            is PipelineStageResult.Failed -> PipelineStageResult.Failed(stream.reason)
        }
    }
}
