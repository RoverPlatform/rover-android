package io.rover.experiences.assets

import io.rover.core.ui.PixelSize
import io.rover.experiences.data.domain.Background
import io.rover.experiences.data.domain.Block
import io.rover.experiences.data.domain.Image
import java.net.URI

interface ImageOptimizationServiceInterface {
    /**
     * Take a given background and return a URI and image configuration that may be used to display
     * it efficiently.  It may perform transforms on the URI and background image configuration to
     * cut down retrieving and decoding an unnecessary larger image than needed for the context.
     *
     * Note that this does not actually perform any sort optimization operation locally.
     *
     * @return The optimized image configuration, which includes the URI with optimization
     * parameters.  May be null if the background in question has no image.
     */
    fun optimizeImageBackground(
        background: Background,
        targetViewPixelSize: PixelSize,
        density: Float
    ): OptimizedImage?

    /**
     * Take a given image block and return the URI with optimization parameters needed to display
     * it.
     *
     * @return optimized URI.
     */
    fun optimizeImageBlock(
        image: Image,
        containingBlock: Block,
        // TODO: perhaps change this to MeasuredSize
        targetViewPixelSize: PixelSize,
        density: Float
    ): URI
}