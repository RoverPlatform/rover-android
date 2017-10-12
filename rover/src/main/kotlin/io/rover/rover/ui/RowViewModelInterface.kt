package io.rover.rover.ui

import android.graphics.Rect

/**
 * View model for Rover UI blocks.
 *
 * Blocks
 */
interface RowViewModelInterface : LayoutableViewModel {
    fun blockViewModels(): List<BlockViewModelInterface>

    fun frame(bounds: Rect): Rect
}
