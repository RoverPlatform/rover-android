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

package io.rover.sdk.core.tracking

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.rover.sdk.core.Rover
import io.rover.sdk.core.events.AppLastSeenInterface
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.whenNotNull

/**
 * Monitors the application lifecycle and emits eventForSessionBoundary to the [SessionTrackerInterface]
 */
class ApplicationSessionEmitter(
    private val lifecycle: Lifecycle,
    private val tracker: SessionTrackerInterface
) {
    private var observer: LifecycleObserver? = null

    fun start() {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    tracker.enterSession(
                        "ApplicationSession",
                        "App Opened",
                        "App Viewed",
                        hashMapOf()
                    )
                    Rover.shared.resolveSingletonOrFail(AppLastSeenInterface::class.java)
                            .markAppLastSeen()
                }

                override fun onPause(owner: LifecycleOwner) {
                    tracker.leaveSession(
                        "ApplicationSession",
                        "App Closed",
                        hashMapOf()
                    )
                }
            }
        )
        log.v("Application lifecycle tracking started.")
    }

    fun stop() {
        observer.whenNotNull { lifecycle.removeObserver(it) }
    }
}
