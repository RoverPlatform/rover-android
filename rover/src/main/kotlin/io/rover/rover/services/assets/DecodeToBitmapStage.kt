package io.rover.rover.services.assets

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.rover.rover.core.logging.log
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
    override fun request(input: URL): Bitmap {
        val stream = priorStage.request(input)

        log.v("Decoding bitmap.")
        return BitmapFactory.decodeStream(stream)
    }
}
