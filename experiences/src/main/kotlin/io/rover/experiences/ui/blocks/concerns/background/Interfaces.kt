package io.rover.experiences.ui.blocks.concerns.background

import android.graphics.Bitmap
import org.reactivestreams.Publisher
import io.rover.core.data.domain.Background
import io.rover.core.ui.BackgroundImageConfiguration
import io.rover.core.ui.concerns.BindableView
import io.rover.core.ui.concerns.BindableViewModel
import io.rover.core.ui.concerns.MeasuredSize
import io.rover.core.ui.concerns.PrefetchAfterMeasure

/**
 * Binds [BackgroundViewModelInterface] properties to that of a view.
 *
 * Backgrounds can specify a background colour or image.
 */
interface ViewBackgroundInterface: BindableView<BackgroundViewModelInterface>

/**
 * This interface is exposed by View Models that have support for a background.  Equivalent to
 * the [Background] domain model interface.
 */
interface BackgroundViewModelInterface: BindableViewModel, PrefetchAfterMeasure {
    val backgroundColor: Int

    /**
     * Subscribe to be informed of the image becoming ready.
     */
    val backgroundUpdates: Publisher<BackgroundUpdate>

    data class BackgroundUpdate(
        val bitmap: Bitmap,
        val fadeIn: Boolean,
        val backgroundImageConfiguration: BackgroundImageConfiguration
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
