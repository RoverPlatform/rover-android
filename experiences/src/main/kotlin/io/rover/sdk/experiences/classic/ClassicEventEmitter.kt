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

package io.rover.sdk.experiences.services

import io.rover.sdk.core.logging.log
import io.rover.sdk.core.streams.PublishSubject
import io.rover.sdk.core.streams.share
import io.rover.sdk.experiences.data.events.MiniAnalyticsEvent
import org.reactivestreams.Publisher

/**
 * Provides a single source for Rover classic events being emitted.
 *
 * You can subscribe to the [trackedEvents] Publisher to be informed of them.
 *
 * Alternatively, you can add a [ClassicExperienceEventListener] to receive event updates
 */
class ClassicEventEmitter {
    private val eventSubject = PublishSubject<MiniAnalyticsEvent>()

    val trackedEvents: Publisher<MiniAnalyticsEvent> by lazy { eventSubject.share() }

    internal fun trackEvent(miniAnalyticsEvent: MiniAnalyticsEvent) {
        eventSubject.onNext(miniAnalyticsEvent)
        log.d("Event emitted: $miniAnalyticsEvent")
        when (miniAnalyticsEvent) {
            is MiniAnalyticsEvent.BlockTapped -> listeners.forEach { it.onClassicBlockTapped(miniAnalyticsEvent) }
            is MiniAnalyticsEvent.ExperienceDismissed -> listeners.forEach {
                it.onClassicExperienceDismissed(
                    miniAnalyticsEvent
                )
            }
            is MiniAnalyticsEvent.ScreenDismissed -> listeners.forEach { it.onClassicScreenDismissed(miniAnalyticsEvent) }
            is MiniAnalyticsEvent.ExperiencePresented -> listeners.forEach {
                it.onClassicExperiencePresented(
                    miniAnalyticsEvent
                )
            }
            is MiniAnalyticsEvent.ExperienceViewed -> listeners.forEach { it.onClassicExperienceViewed(miniAnalyticsEvent) }
            is MiniAnalyticsEvent.ScreenViewed -> listeners.forEach { it.onClassicScreenViewed(miniAnalyticsEvent) }
            is MiniAnalyticsEvent.ScreenPresented -> listeners.forEach { it.onClassicScreenPresented(miniAnalyticsEvent) }
            is MiniAnalyticsEvent.PollAnswered -> listeners.forEach { it.onClassicPollAnswered(miniAnalyticsEvent) }
            is MiniAnalyticsEvent.AppOpened -> { /* no-op */ }
        }
    }

    private val listeners: MutableList<ClassicExperienceEventListener> = mutableListOf()

    fun addClassicExperienceEventListener(listener: ClassicExperienceEventListener) {
        listeners.add(listener)
    }

    fun removeClassicExperienceEventListener(listener: ClassicExperienceEventListener) {
        listeners.remove(listener)
    }

    fun removeAllListeners() {
        listeners.clear()
    }
}

interface ClassicExperienceEventListener {
    fun onClassicBlockTapped(event: MiniAnalyticsEvent.BlockTapped) {}

    fun onClassicExperienceDismissed(event: MiniAnalyticsEvent.ExperienceDismissed) {}

    fun onClassicScreenDismissed(event: MiniAnalyticsEvent.ScreenDismissed) {}

    fun onClassicExperiencePresented(event: MiniAnalyticsEvent.ExperiencePresented) {}

    fun onClassicExperienceViewed(event: MiniAnalyticsEvent.ExperienceViewed) {}

    fun onClassicScreenViewed(event: MiniAnalyticsEvent.ScreenViewed) {}

    fun onClassicScreenPresented(event: MiniAnalyticsEvent.ScreenPresented) {}

    fun onClassicPollAnswered(event: MiniAnalyticsEvent.PollAnswered) {}
}
