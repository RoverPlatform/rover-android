package io.rover.sdk.experiences.ui.blocks.poll.image

import io.rover.sdk.experiences.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.experiences.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.sdk.experiences.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.sdk.experiences.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.sdk.experiences.ui.layout.ViewType

internal class ImagePollBlockViewModel(
        private val imagePollViewModel: ImagePollViewModelInterface,
        private val blockViewModel: BlockViewModelInterface,
        private val backgroundViewModel: BackgroundViewModelInterface,
        private val borderViewModel: BorderViewModelInterface
) : CompositeBlockViewModelInterface,
    BlockViewModelInterface by blockViewModel,
    BackgroundViewModelInterface by backgroundViewModel,
    BorderViewModelInterface by borderViewModel,
    ImagePollViewModelInterface by imagePollViewModel {
    override val viewType: ViewType = ViewType.ImagePoll
}