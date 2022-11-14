package io.rover.sdk.experiences.ui.blocks.image

import io.rover.sdk.experiences.ui.layout.ViewType
import io.rover.sdk.experiences.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.experiences.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.sdk.experiences.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.sdk.experiences.ui.concerns.MeasuredSize

internal class ImageBlockViewModel(
        private val blockViewModel: BlockViewModelInterface,
        private val backgroundViewModel: BackgroundViewModelInterface,
        private val imageViewModel: ImageViewModelInterface,
        private val borderViewModel: BorderViewModelInterface
) : ImageBlockViewModelInterface,
    BlockViewModelInterface by blockViewModel,
    ImageViewModelInterface by imageViewModel,
    BackgroundViewModelInterface by backgroundViewModel,
    BorderViewModelInterface by borderViewModel {
    override val viewType: ViewType = ViewType.Image

    override fun informDimensions(measuredSize: MeasuredSize) {
        backgroundViewModel.informDimensions(measuredSize)
        imageViewModel.informDimensions(measuredSize)
    }

    override fun measuredSizeReadyForPrefetch(measuredSize: MeasuredSize) {
        backgroundViewModel.measuredSizeReadyForPrefetch(measuredSize)
        imageViewModel.measuredSizeReadyForPrefetch(measuredSize)
    }
}
