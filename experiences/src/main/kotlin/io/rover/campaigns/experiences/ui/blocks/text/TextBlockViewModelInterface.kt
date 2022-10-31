package io.rover.campaigns.experiences.ui.blocks.text

import io.rover.campaigns.experiences.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.campaigns.experiences.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.campaigns.experiences.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.campaigns.experiences.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.campaigns.experiences.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.campaigns.experiences.ui.blocks.concerns.text.TextViewModelInterface

/**
 * View Model for a block that contains rich text content (decorated with strong, italic, and
 * underline HTML tags).
 */
internal interface TextBlockViewModelInterface :
    CompositeBlockViewModelInterface,
    LayoutableViewModel,
    BlockViewModelInterface,
    BackgroundViewModelInterface,
    BorderViewModelInterface,
    TextViewModelInterface