package io.rover.sdk.experiences.ui.blocks.poll.text

import io.rover.sdk.experiences.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.experiences.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.sdk.experiences.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.sdk.experiences.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.sdk.experiences.ui.layout.ViewType

internal class TextPollBlockViewModel(
        private val textPollViewModel: TextPollViewModelInterface,
        private val blockViewModel: BlockViewModelInterface,
        private val backgroundViewModel: BackgroundViewModelInterface,
        private val borderViewModel: BorderViewModelInterface
) : CompositeBlockViewModelInterface,
    BlockViewModelInterface by blockViewModel,
    BackgroundViewModelInterface by backgroundViewModel,
    BorderViewModelInterface by borderViewModel,
    TextPollViewModelInterface by textPollViewModel {
    override val viewType: ViewType = ViewType.TextPoll
}