package io.rover.sdk.experiences.assets

import io.rover.sdk.experiences.ui.BackgroundImageConfiguration
import java.net.URI

/**
 * A retrieval URI and configuration needed for displaying an image.
 */
internal data class OptimizedImage(
    /**
     * The (potentially) modified URI.
     */
    val uri: URI,

    /**
     * The (potentially) modified image display configuration.
     */
    val imageConfiguration: BackgroundImageConfiguration
)