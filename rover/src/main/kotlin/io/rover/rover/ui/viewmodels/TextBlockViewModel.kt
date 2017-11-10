package io.rover.rover.ui.viewmodels

import io.rover.rover.ui.types.ViewType

class TextBlockViewModel(
    private val blockViewModel: BlockViewModelInterface,
    private val textViewModel: TextViewModelInterface,
    private val backgroundViewModel: BackgroundViewModelInterface,
    private val borderViewModel: BorderViewModelInterface
) : TextBlockViewModelInterface,
    BlockViewModelInterface by blockViewModel,
    BackgroundViewModelInterface by backgroundViewModel,
    TextViewModelInterface by textViewModel,
    BorderViewModelInterface by borderViewModel {

    override val viewType: ViewType = ViewType.Text
}
