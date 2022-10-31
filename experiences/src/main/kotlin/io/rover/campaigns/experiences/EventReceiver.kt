package io.rover.campaigns.experiences

import io.rover.campaigns.core.data.domain.Attributes
import io.rover.campaigns.core.events.EventQueueServiceInterface
import io.rover.campaigns.core.events.domain.Event
import io.rover.campaigns.core.streams.subscribe
import io.rover.sdk.data.domain.Block
import io.rover.sdk.data.domain.Experience
import io.rover.sdk.data.domain.Screen
import io.rover.sdk.data.events.Option
import io.rover.sdk.data.events.RoverEvent
import io.rover.sdk.services.EventEmitter

/**
 * Receive events emitted by the Rover SDK.
 */
open class EventReceiver(
    private val eventQueueService: EventQueueServiceInterface
) {
    open fun startListening(emitter: EventEmitter) {
        emitter.trackedEvents.subscribe { event ->

            when (event) {
                is RoverEvent.BlockTapped -> eventQueueService.trackEvent(event.transformToEvent(), "rover")
                is RoverEvent.ExperienceDismissed -> eventQueueService.trackEvent(event.transformToEvent(), "rover")
                is RoverEvent.ScreenDismissed -> eventQueueService.trackEvent(event.transformToEvent(), "rover")
                is RoverEvent.ExperiencePresented -> eventQueueService.trackEvent(event.transformToEvent(), "rover")
                is RoverEvent.ExperienceViewed -> eventQueueService.trackEvent(event.transformToEvent(), "rover")
                is RoverEvent.ScreenViewed -> eventQueueService.trackEvent(event.transformToEvent(), "rover")
                is RoverEvent.ScreenPresented -> eventQueueService.trackEvent(event.transformToEvent(), "rover")
                is RoverEvent.PollAnswered -> eventQueueService.trackEvent(event.transformToEvent(), "rover")
                is RoverEvent.AppOpened -> { /* not tracked */ }
            }
        }
    }
}

private fun experienceAttributes(experience: Experience, campaignId: String?) = mapOf(
    "id" to experience.id,
    "name" to experience.name,
    "keys" to experience.keys,
    "tags" to experience.tags
) + if (campaignId != null) hashMapOf("campaignID" to campaignId) else hashMapOf()

private fun screenAttributes(screen: Screen) = mapOf(
    "id" to screen.id,
    "name" to screen.name,
    "keys" to screen.keys,
    "tags" to screen.tags
)

private fun blockAttributes(block: Block) = mapOf(
    "id" to block.id,
    "name" to block.name,
    "keys" to block.keys,
    "tags" to block.tags
)

private fun optionAttributes(option: Option) = mapOf(
    "id" to option.id,
    "text" to option.text
) + if (option.image != null) hashMapOf("image" to option.image) else hashMapOf()

private fun RoverEvent.PollAnswered.transformToEvent(): Event {
    val attributes: Attributes = mapOf(
        "experience" to experienceAttributes(experience, campaignId),
        "screen" to screenAttributes(screen),
        "block" to blockAttributes(block),
        "option" to optionAttributes(option)
    )

    return Event("Poll Answered", attributes)
}

private fun RoverEvent.BlockTapped.transformToEvent(): Event {
    val attributes: Attributes = mapOf(
        "experience" to experienceAttributes(experience, campaignId),
        "screen" to screenAttributes(screen),
        "block" to blockAttributes(block)
    )

    return Event("Block Tapped", attributes)
}

private fun RoverEvent.ExperienceDismissed.transformToEvent(): Event {
    return Event(
        "Experience Dismissed",
        mapOf("experience" to experienceAttributes(experience, campaignId))
    )
}

private fun RoverEvent.ScreenDismissed.transformToEvent(): Event {
    val attributes: Attributes = mapOf(
        "experience" to experienceAttributes(experience, campaignId),
        "screen" to screenAttributes(screen)
    )

    return Event("Screen Dismissed", attributes)
}

private fun RoverEvent.ExperiencePresented.transformToEvent(): Event {
    return Event(
        "Experience Presented",
        mapOf("experience" to experienceAttributes(experience, campaignId))
    )
}

private fun RoverEvent.ExperienceViewed.transformToEvent(): Event {
    val attributes: Attributes = mapOf(
        "experience" to experienceAttributes(experience, campaignId),
        "duration" to duration
    )

    return Event("Experience Viewed", attributes)
}

private fun RoverEvent.ScreenViewed.transformToEvent(): Event {
    val attributes: Attributes = mapOf(
        "experience" to experienceAttributes(experience, campaignId),
        "screen" to screenAttributes(screen),
        "duration" to duration
    )

    return Event("Screen Viewed", attributes)
}

private fun RoverEvent.ScreenPresented.transformToEvent(): Event {
    val attributes: Attributes = mapOf(
        "experience" to experienceAttributes(experience, campaignId),
        "screen" to screenAttributes(screen)
    )

    return Event("Screen Presented", attributes)
}
