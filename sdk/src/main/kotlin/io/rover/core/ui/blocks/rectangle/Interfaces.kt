package io.rover.core.ui.blocks.rectangle

import io.rover.core.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.core.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.core.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.core.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.core.ui.blocks.concerns.layout.LayoutableViewModel

/**
 * View Model for a block that contains no content (other than its own border and
 * background).
 */
interface RectangleBlockViewModelInterface : CompositeBlockViewModelInterface, LayoutableViewModel, BlockViewModelInterface, BackgroundViewModelInterface, BorderViewModelInterface