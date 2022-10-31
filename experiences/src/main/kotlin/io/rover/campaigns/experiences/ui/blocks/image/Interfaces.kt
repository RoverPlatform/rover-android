package io.rover.campaigns.experiences.ui.blocks.image

import android.graphics.Bitmap
import io.rover.campaigns.experiences.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.campaigns.experiences.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.campaigns.experiences.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.campaigns.experiences.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.campaigns.experiences.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.campaigns.experiences.ui.blocks.concerns.layout.Measurable
import io.rover.campaigns.experiences.ui.concerns.MeasuredBindableView
import io.rover.campaigns.experiences.ui.concerns.BindableViewModel
import io.rover.campaigns.experiences.ui.concerns.MeasuredSize
import io.rover.campaigns.experiences.ui.concerns.PrefetchAfterMeasure
import org.reactivestreams.Publisher

// ViewImage mixin is binding against ImageBlockViewModelInterface instead of
// ImageViewModelInterface in order to discover the block's opacity for use in an animation.  This
// is a hack and should be solved properly.
internal interface ViewImageInterface : MeasuredBindableView<ImageBlockViewModelInterface>

internal interface ImageViewModelInterface : Measurable, BindableViewModel, PrefetchAfterMeasure {
    /**
     * Subscribe to be informed of the image becoming ready.
     */
    val imageUpdates: Publisher<ImageUpdate>

    val isDecorative: Boolean

    val accessibilityLabel: String?

    data class ImageUpdate(
        val bitmap: Bitmap,
        val fadeIn: Boolean
    )

    /**
     * Inform the view model of the display geometry of the image view, so that it may
     * make an attempt to retrieve the image for display.
     *
     * Be sure to subscribe to [imageUpdates] first.
     */
    fun informDimensions(
        measuredSize: MeasuredSize
    )
}

internal interface ImageBlockViewModelInterface :
    CompositeBlockViewModelInterface,
    LayoutableViewModel,
    BlockViewModelInterface,
    BackgroundViewModelInterface,
    BorderViewModelInterface,
    ImageViewModelInterface
