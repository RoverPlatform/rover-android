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

import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.core.tracking.ConversionsTrackerService
import io.rover.sdk.experiences.data.events.MiniAnalyticsEvent
import io.rover.sdk.experiences.services.ClassicEventEmitter
import java.util.Locale

internal fun ConversionsTrackerService.startListening(emitter: ClassicEventEmitter) {
    emitter.trackedEvents.subscribe { event ->
        getConversion(event)?.let { tag ->
            trackConversion(tag)
        }
    }
}

private fun getConversion(event: MiniAnalyticsEvent): String? =
        when (event) {
            is MiniAnalyticsEvent.BlockTapped -> event.block.conversion?.let {
                it.tag
            }
            is MiniAnalyticsEvent.ScreenPresented -> event.screen.conversion?.let {
                it.tag
            }
            // NOTE: We always append the poll's option to the tag
            is MiniAnalyticsEvent.PollAnswered -> event.block.conversion?.let {
                val pollTag = event.option.text.replace(" ", "_").lowercase(Locale.ROOT)
                "${it.tag}_$pollTag"
            }
            else -> null
        }
