package io.rover.campaigns.experiences.ui.blocks.button

import io.rover.campaigns.experiences.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.campaigns.experiences.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.campaigns.experiences.ui.layout.ViewType
import io.rover.campaigns.experiences.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.campaigns.experiences.ui.blocks.concerns.text.TextViewModelInterface

internal class ButtonBlockViewModel(
    blockViewModel: BlockViewModelInterface,
    private val borderViewModel: BorderViewModelInterface,
    private val backgroundViewModel: BackgroundViewModelInterface,
    private val textViewModel: TextViewModelInterface
) : ButtonBlockViewModelInterface,
    BlockViewModelInterface by blockViewModel,
    BorderViewModelInterface by borderViewModel,
    BackgroundViewModelInterface by backgroundViewModel,
    TextViewModelInterface by textViewModel {
    override val viewType: ViewType = ViewType.Button
}
