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

@file:OptIn(RoverBenchSupportApi::class)

package io.rover.sdk.core.benchsupport

import io.rover.sdk.core.data.config.RoverConfig
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory holder for the Bench overrides, and the seam the config pipeline applies them
 * through.
 *
 * Deliberately process-global rather than a DI singleton: the bench sets overrides before
 * `Rover.initialize` so the very first config the SDK publishes is already overridden, and
 * ordering constraints between the bench's `Application.onCreate` and the container's
 * assembly are not worth the trouble. Nothing here is ever persisted — the managers keep
 * caching the *backend* values, and the overrides evaporate with the process.
 *
 * Change notification is a synchronous callback list rather than a flow so that a setter and
 * the resulting re-emission happen on the caller's thread, with no window in which a
 * consumer could observe a stale value. The registered managers are container singletons, so
 * nothing deregisters in production; tests construct managers per case and clear the list in
 * teardown via [removeAllChangeListeners].
 *
 * @see RoverBenchSupport for the public entry point.
 */
internal object BenchSupportOverrideStore {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var configOverrides: RoverConfigOverrides = RoverConfigOverrides()
        set(value) {
            field = value
            notifyChanged()
        }

    @Volatile
    var homeExperienceURLOverride: Override<String> = Override.NoOverride
        set(value) {
            field = value
            notifyChanged()
        }

    /**
     * Returns [config] with the current config overrides layered on top.
     */
    fun applyToConfig(config: RoverConfig): RoverConfig = configOverrides.applyTo(config)

    /**
     * Returns [experienceURL] with the current home view URL override layered on top.
     */
    fun applyToHomeExperienceURL(experienceURL: String?): String? =
        homeExperienceURLOverride.resolve(experienceURL)

    /**
     * Registers a callback invoked, synchronously, whenever any override changes. Callers are
     * expected to re-apply the overrides to their most recent backend value and re-publish.
     */
    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    /**
     * Drops every registered listener. Test teardown only: in production the registered
     * managers are container singletons that live as long as the process, but tests construct
     * them per case, and without this each case's managers would stay registered — and be
     * notified into dead mocks — for the rest of the run.
     */
    fun removeAllChangeListeners() {
        listeners.clear()
    }

    /**
     * Drops every override, returning the SDK to backend-provided values.
     */
    fun clear() {
        configOverrides = RoverConfigOverrides()
        homeExperienceURLOverride = Override.NoOverride
    }

    private fun notifyChanged() {
        listeners.forEach { it() }
    }
}
