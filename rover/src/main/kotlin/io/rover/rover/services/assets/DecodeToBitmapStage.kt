package io.rover.rover.services.assets

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.BufferedInputStream
import java.net.URL

/**
 * This pipeline stage decodes an incoming stream into a bitmap.  It implicitly supports (and auto-detects
 * any image format that Android supports).
 *
 * TODO mention image formats explicitly supported by Rover.
 */
class DecodeToBitmapStage(
    private val priorStage: SynchronousPipelineStage<URL, BufferedInputStream>
): SynchronousPipelineStage<URL, Bitmap> {
    override fun request(input: URL): Bitmap {
        val stream = priorStage.request(input)

        return BitmapFactory.decodeStream(stream)
    }
}
