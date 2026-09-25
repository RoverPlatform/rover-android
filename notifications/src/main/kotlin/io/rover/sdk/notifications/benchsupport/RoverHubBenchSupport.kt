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

package io.rover.sdk.notifications.benchsupport

import io.rover.sdk.core.Rover
import io.rover.sdk.core.benchsupport.RoverBenchSupportApi
import io.rover.sdk.notifications.communicationhub.sync.HubSyncCoordinator

/**
 * Bench support hooks for the Communication Hub.
 *
 * The Hub's counterpart to `io.rover.sdk.core.benchsupport.RoverBenchSupport`, in the module that
 * owns the Hub — `core` cannot see the notifications module, so the Hub's hook cannot live
 * alongside the configuration overrides. Same shape, same opt-in marker, same promise: none of
 * this is visible to an integrator working from the normal SDK entry points, and none of it is
 * supported.
 *
 * @see RoverBenchSupportApi
 */
@RoverBenchSupportApi
public object RoverHubBenchSupport {
    /**
     * Drops every locally stored Hub row, so the Hub refetches from the server on next appearance.
     *
     * ## Why the SDK has to do this, rather than the bench deleting the database
     *
     * The Engage database is a process-wide Room singleton, and every repository and sync
     * participant captures its DAOs at construction. Deleting the database *files* from outside
     * leaves that connection open on unlinked inodes: rows the reset was supposed to drop are
     * still served from the live connection, sync cursors still resume mid-stream, and everything
     * written afterwards goes to a file nothing will ever open again. It only appears to work when
     * the caller exits the process immediately afterwards.
     *
     * So this runs the SDK's own coordinated reset instead: active sync work is cancelled and
     * joined, the rows are deleted in one transaction on the live connection, delivered Hub
     * notifications are cleared from the tray, and the repositories are told, so Room's `Flow`
     * queries re-emit and any open conversation screen dismisses. A response already in flight
     * cannot repopulate what was just cleared, because the reset advances the sync generation the
     * writers check before they persist.
     *
     * Suspends until the reset has completed. Safe to call while the Hub is on screen.
     */
    public suspend fun resetHubData() {
        val coordinator = Rover.shared.resolve(HubSyncCoordinator::class.java)
            ?: throw RuntimeException(
                "Dependency not registered: HubSyncCoordinator. Did you include " +
                    "NotificationsAssembler() in the assembler list?"
            )
        coordinator.resetHubDataOnDemand()
    }
}
