package io.rover.sdk.ui.blocks.concerns.border

import io.rover.sdk.data.domain.Border
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.BindableViewModel

/**
 * Binds [BorderViewModelInterface] properties to that of a view.
 *
 * Borders can specify a border of arbitrary width, with optional rounded corners.
 */
interface ViewBorderInterface : MeasuredBindableView<BorderViewModelInterface>

/**
 * This interface is exposed by View Models that have support for a border (of arbitrary width and
 * possibly rounded with a radius).  Equivalent to the [Border] domain model interface.
 */
interface BorderViewModelInterface : BindableViewModel {
    /**
     * An Android color ARGB int of the border color.
     */
    val borderColor: Int

    /**
     * The rounded corner radius of the border in Dp.
     */
    val borderRadius: Int

    /**
     * The border width in Dp.
     */
    val borderWidth: Int

    companion object
}
