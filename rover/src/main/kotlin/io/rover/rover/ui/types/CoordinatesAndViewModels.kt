package io.rover.rover.ui.types

import android.graphics.Rect
import io.rover.rover.ui.viewmodels.LayoutableViewModel

/**
 * A sequence of [LayoutableViewModel]s in two-dimensional space, as an output of a layout pass.
 */
typealias CoordinatesAndViewModels = List<Pair<Rect, LayoutableViewModel>>
