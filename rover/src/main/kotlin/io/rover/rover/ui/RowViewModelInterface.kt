package io.rover.rover.ui

import android.graphics.Rect

/**
 * View model for Rover UI blocks.
 */
interface RowViewModelInterface : LayoutableViewModel, BackgroundViewModelInterface {
    fun blockViewModels(): List<BlockViewModelInterface>
}
