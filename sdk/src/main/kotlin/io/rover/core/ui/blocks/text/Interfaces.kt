package io.rover.core.ui.blocks.text

import io.rover.core.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.core.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.core.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.core.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.core.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.core.ui.blocks.concerns.text.TextViewModelInterface

/**
 * View Model for a block that contains rich text content (decorated with strong, italic, and
 * underline HTML tags).
 */
interface TextBlockViewModelInterface :
    CompositeBlockViewModelInterface,
    LayoutableViewModel,
    BlockViewModelInterface,
    BackgroundViewModelInterface,
    BorderViewModelInterface,
    TextViewModelInterface