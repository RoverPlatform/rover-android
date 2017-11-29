package io.rover.rover.ui.viewmodels

import io.rover.rover.ui.types.ViewType

class RectangleBlockViewModel(
    blockViewModel: BlockViewModelInterface,
    backgroundViewModel: BackgroundViewModelInterface,
    borderViewModel: BorderViewModel
) : RectangleBlockViewModelInterface,
    BlockViewModelInterface by blockViewModel,
    BackgroundViewModelInterface by backgroundViewModel,
    BorderViewModelInterface by borderViewModel {
    override val viewType: ViewType = ViewType.Rectangle
}
