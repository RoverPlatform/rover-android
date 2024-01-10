/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.experiences

import io.rover.sdk.core.data.domain.Attributes
import io.rover.sdk.core.data.domain.Block
import io.rover.sdk.core.data.domain.ClassicExperienceModel
import io.rover.sdk.core.data.domain.Screen
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.events.domain.Event
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.experiences.data.events.MiniAnalyticsEvent
import io.rover.sdk.experiences.data.events.Option
import io.rover.sdk.experiences.services.ClassicEventEmitter

/**
 * Receive events emitted by the Classic Experiences renderer in order to track those events into the
 * Rover event queue. Hangs off of the mini analytics service to do so.
 */
internal class ClassicEventDispatcher(
    private val eventQueueService: EventQueueServiceInterface
) {
    fun startListening(emitter: ClassicEventEmitter) {
        emitter.trackedEvents.subscribe { event ->

            when (event) {
                is MiniAnalyticsEvent.BlockTapped -> eventQueueService.trackEvent(event.transformToEvent(), "rover")
                is MiniAnalyticsEvent.ExperienceDismissed -> eventQueueService.trackEvent(event.transformToEvent(), "rover")
                is MiniAnalyticsEvent.ScreenDismissed -> eventQueueService.trackEvent(event.transformToEvent(), "rover")
                is MiniAnalyticsEvent.ExperiencePresented -> eventQueueService.trackEvent(event.transformToEvent(), "rover")
                is MiniAnalyticsEvent.ExperienceViewed -> eventQueueService.trackEvent(event.transformToEvent(), "rover")
                is MiniAnalyticsEvent.ScreenViewed -> eventQueueService.trackEvent(event.transformToEvent(), "rover")
                is MiniAnalyticsEvent.ScreenPresented -> eventQueueService.trackEvent(event.transformToEvent(), "rover")
                is MiniAnalyticsEvent.PollAnswered -> eventQueueService.trackEvent(event.transformToEvent(), "rover")
                is MiniAnalyticsEvent.AppOpened -> { /* not tracked */ }
            }
        }
    }
}

private fun experienceAttributes(classicExperience: ClassicExperienceModel, campaignId: String?) = mapOf(
    "id" to classicExperience.id,
    "name" to classicExperience.name,
    "keys" to classicExperience.keys,
    "tags" to classicExperience.tags,
    "url" to classicExperience.sourceUrl.toString(),
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

private fun MiniAnalyticsEvent.PollAnswered.transformToEvent(): Event {
    val attributes: Attributes = mapOf(
        "experience" to experienceAttributes(experience, campaignId),
        "screen" to screenAttributes(screen),
        "block" to blockAttributes(block),
        "option" to optionAttributes(option)
    )

    return Event("Classic Poll Answered", attributes)
}

private fun MiniAnalyticsEvent.BlockTapped.transformToEvent(): Event {
    val attributes: Attributes = mapOf(
        "experience" to experienceAttributes(experience, campaignId),
        "screen" to screenAttributes(screen),
        "block" to blockAttributes(block)
    )

    return Event("Classic Block Tapped", attributes)
}

private fun MiniAnalyticsEvent.ExperienceDismissed.transformToEvent(): Event {
    return Event(
        "Classic Experience Dismissed",
        mapOf("experience" to experienceAttributes(experience, campaignId))
    )
}

private fun MiniAnalyticsEvent.ScreenDismissed.transformToEvent(): Event {
    val attributes: Attributes = mapOf(
        "experience" to experienceAttributes(experience, campaignId),
        "screen" to screenAttributes(screen)
    )

    return Event("Classic Screen Dismissed", attributes)
}

private fun MiniAnalyticsEvent.ExperiencePresented.transformToEvent(): Event {
    return Event(
        "Classic Experience Presented",
        mapOf("experience" to experienceAttributes(experience, campaignId))
    )
}

private fun MiniAnalyticsEvent.ExperienceViewed.transformToEvent(): Event {
    val attributes: Attributes = mapOf(
        "experience" to experienceAttributes(experience, campaignId),
        "duration" to duration
    )

    return Event("Classic Experience Viewed", attributes)
}

private fun MiniAnalyticsEvent.ScreenViewed.transformToEvent(): Event {
    val attributes: Attributes = mapOf(
        "experience" to experienceAttributes(experience, campaignId),
        "screen" to screenAttributes(screen),
        "duration" to duration
    )

    return Event("Classic Screen Viewed", attributes)
}

private fun MiniAnalyticsEvent.ScreenPresented.transformToEvent(): Event {
    val attributes: Attributes = mapOf(
        "experience" to experienceAttributes(experience, campaignId),
        "screen" to screenAttributes(screen)
    )

    return Event("Classic Screen Presented", attributes)
}
