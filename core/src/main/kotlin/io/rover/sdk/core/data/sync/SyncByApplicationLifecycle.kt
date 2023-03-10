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

package io.rover.sdk.core.data.sync

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.logging.log

/**
 * Run syncs (and event queue flushes) by process lifecycle.
 */
class SyncByApplicationLifecycle(
    private val syncCoordinator: SyncCoordinatorInterface,
    private val eventQueueService: EventQueueServiceInterface,
    private val processLifecycle: Lifecycle
) {
    private var observer: LifecycleObserver = object : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
        fun appResume() {
            log.v("App foregrounded, triggering sync.")
            syncCoordinator.triggerSync()
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        fun appPause() {
            log.v("App backgrounded, triggering event flush.")
            eventQueueService.flushNow()
        }
    }

    fun start() {
        processLifecycle.addObserver(observer)
    }

    fun stop() {
        processLifecycle.removeObserver(observer)
    }
}
