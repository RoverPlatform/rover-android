package io.rover.experiences.ui.blocks.button

import io.rover.experiences.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.experiences.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.experiences.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.experiences.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.experiences.ui.blocks.concerns.layout.BlockViewModel
import io.rover.experiences.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.experiences.ui.blocks.concerns.text.TextViewModelInterface
import io.rover.core.ui.concerns.BindableViewModel

/**
 * View Model for block content that contains a button (but only the presentation aspects thereof).
 *
 * Note that if you're looking for the Click event/handling itself, that is handled in
 * [BlockViewModel].
 */
interface ButtonViewModelInterface: BindableViewModel

interface ButtonBlockViewModelInterface :
    CompositeBlockViewModelInterface,
    LayoutableViewModel,
    BlockViewModelInterface,
    BackgroundViewModelInterface,
    BorderViewModelInterface,
    TextViewModelInterface
