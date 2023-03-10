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

import android.app.Activity
import io.rover.sdk.core.Rover
import io.rover.sdk.core.data.domain.Block
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.experiences.data.events.MiniAnalyticsEvent
import io.rover.sdk.experiences.services.CustomActionActivated
import io.rover.sdk.experiences.services.EventEmitter
import io.rover.sdk.experiences.services.ExperienceScreenViewed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Call this method with a callback to register to be informed of screen views in Rover Experiences.
 *
 * This also supports classic experiences.
 */
fun Rover.registerScreenViewedCallback(callback: (ScreenViewDetails) -> Unit) {
    resolveSingletonOrFail(RoverExperiencesClassic::class.java).classicEventEmitter.trackedEvents.subscribe { event ->
        when (event) {
            // using the "Presented" classic event instead of the classic "Viewed" event,
            // because the classic viewed event has extra "dwell" logic that introduces
            // a delay, whereas our new event interface contract eschews that.
            is MiniAnalyticsEvent.ScreenPresented -> {
                callback(
                    ScreenViewDetails(
                        experienceName = event.experience.name,
                        experienceId = event.experience.id,
                        screenName = event.screen.name,
                        screenId = event.screen.id,
                        screenTags = event.screen.tags,
                        screenProperties = event.screen.keys,
                        campaignId = event.campaignId,

                        // data context and URL params not supported by classic experiences.
                        data = null,
                        urlParameters = emptyMap()
                    )
                )
            }
            else -> {
                // ignore
            }
        }
    }

    val eventEmitter = resolveSingletonOrFail(EventEmitter::class.java)

    eventEmitter
        .events
        .onEach { experienceEvent ->
            when (experienceEvent) {
                is ExperienceScreenViewed -> {
                    CoroutineScope(Dispatchers.Main).launch {
                        callback(
                            ScreenViewDetails(
                                experienceName = experienceEvent.experienceName,
                                experienceId = experienceEvent.experienceId,
                                screenName = experienceEvent.screenName,
                                screenId = experienceEvent.screenId,
                                screenTags = experienceEvent.screenTags,
                                screenProperties = experienceEvent.screenProperties,
                                data = experienceEvent.data,
                                urlParameters = experienceEvent.urlParameters,
                                campaignId = experienceEvent.campaignId
                            )
                        )
                    }
                }
            }
        }.launchIn(CoroutineScope(Dispatchers.IO))
}

/**
 * Call this method with a callback to register to be informed of custom action activations
 * in Rover Experiences.
 *
 * This also supports classic experiences.
 */
fun Rover.registerCustomActionCallback(callback: (CustomActionActivation) -> Unit) {
    resolveSingletonOrFail(RoverExperiencesClassic::class.java).classicEventEmitter.trackedEvents.subscribe { event ->
        when (event) {
            is MiniAnalyticsEvent.BlockTapped -> {
                if (event.block.tapBehavior is Block.TapBehavior.Custom) {
                    callback(
                        CustomActionActivation(
                            experienceName = event.experience.name,
                            experienceId = event.experience.id,
                            screenName = event.screen.name,
                            screenId = event.screen.id,
                            screenTags = event.screen.tags,
                            screenProperties = event.screen.keys,
                            campaignId = event.campaignId,

                            // data context and URL params not supported by classic experiences.
                            data = null,
                            urlParameters = emptyMap(),

                            nodeName = event.block.name,
                            nodeId = event.block.id,
                            nodeTags = event.block.tags,
                            nodeProperties = event.block.keys,

                            // providing activity not supported with classic experiences
                            activity = null
                        )
                    )
                }
            }
            else -> {
                // ignore
            }
        }
    }

    val eventEmitter = resolveSingletonOrFail(EventEmitter::class.java)

    eventEmitter
        .events
        .onEach { event ->
            when (event) {
                is CustomActionActivated -> {
                    CoroutineScope(Dispatchers.Main).launch {
                        callback(
                            CustomActionActivation(
                                experienceName = event.experienceName,
                                experienceId = event.experienceId,
                                screenName = event.screenName,
                                screenId = event.screenId,
                                screenTags = event.screenTags,
                                screenProperties = event.screenProperties,
                                data = event.data,
                                urlParameters = event.urlParameters,
                                campaignId = event.campaignId,
                                nodeName = event.nodeName,
                                nodeId = event.nodeId,
                                nodeTags = event.nodeTags,
                                nodeProperties = event.nodeProperties,
                                activity = event.activity
                            )
                        )
                    }
                }
            }
        }.launchIn(CoroutineScope(Dispatchers.IO))
}

data class ScreenViewDetails(
    val experienceName: String?,
    val experienceId: String?,
    val screenName: String?,
    val screenId: String,
    val screenTags: List<String>,
    val screenProperties: Map<String, String>,
    val data: Any?,
    val urlParameters: Map<String, String>,
    val campaignId: String?
)

data class CustomActionActivation(
    val experienceName: String?,
    val experienceId: String?,
    val screenName: String?,
    val screenId: String,
    val screenTags: List<String>,
    val screenProperties: Map<String, String>,
    val data: Any?,
    val urlParameters: Map<String, String>,
    val campaignId: String?,
    val nodeName: String?,
    val nodeId: String,
    val nodeTags: List<String>,
    val nodeProperties: Map<String, String>,
    val activity: Activity?
)
