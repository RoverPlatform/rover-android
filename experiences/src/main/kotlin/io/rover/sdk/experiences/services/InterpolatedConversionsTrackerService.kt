package io.rover.sdk.experiences.services

import io.rover.sdk.core.tracking.ConversionsTrackerService
import io.rover.sdk.experiences.rich.compose.ui.data.DataContext
import io.rover.sdk.experiences.rich.compose.ui.data.Interpolator


internal class InterpolatedConversionsTrackerService(
    private val conversionsTrackerService: ConversionsTrackerService
) {
    fun trackConversion(
        tag: String,
        dataContext: DataContext
    ) {
        val interpolator = Interpolator(
                dataContext
        )
        val interpolatedTag = interpolator.interpolate(tag)

        interpolatedTag?.let {
            conversionsTrackerService.trackConversion(it)
        } ?: conversionsTrackerService.trackConversion(tag)
    }

    fun trackConversions(
        tags: List<String>,
        dataContext: DataContext
    ) {
        tags.forEach { trackConversion(tag = it, dataContext) }
    }
}