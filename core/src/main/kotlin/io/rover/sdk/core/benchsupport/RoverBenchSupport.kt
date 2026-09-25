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

package io.rover.sdk.core.benchsupport

import io.rover.sdk.core.data.config.RoverConfig

/**
 * Entry point for the Bench support hooks.
 *
 * Deliberately a standalone object rather than an extension of `Rover`, so none of this
 * appears in autocompletion for integrators working from the normal SDK entry points.
 *
 * ## Overrides are a layer, not a value
 *
 * Overrides do not replace the backend configuration; they are re-applied to it. The SDK
 * re-syncs [RoverConfig] on every Hub appearance and on the usual sync cadence, and the Hub
 * home view URL is re-fetched alongside it. Each time a fresh backend value arrives, what is
 * published to observers is that value with these overrides layered on top, so an override
 * survives every refresh and un-setting one restores the live backend value on the spot.
 *
 * Setting an override re-publishes immediately if a value is already loaded, so the Hub
 * restyles or re-renders without waiting for the next sync.
 *
 * Overrides live in memory only. The SDK never persists them (it goes on caching the
 * *backend* values), so anything that should survive a relaunch has to be re-applied by the
 * host app at startup — before `Rover.initialize` if the very first published config needs to
 * be overridden.
 *
 * ```kotlin
 * @OptIn(RoverBenchSupportApi::class)
 * fun applyBenchSettings() {
 *     RoverBenchSupport.configOverrides = RoverConfigOverrides(
 *         isHomeEnabled = Override.Value(true),
 *         hubDeepLink = Override.Value("testbench-deep-link://tab/hub")
 *     )
 *     RoverBenchSupport.homeExperienceURLOverride = Override.Value(homeURL)
 * }
 * ```
 */
@RoverBenchSupportApi
public object RoverBenchSupport {
    /**
     * Overrides layered over the backend-provided [RoverConfig] on every refresh.
     *
     * Assign a copy to change one field, e.g.
     * `configOverrides = configOverrides.copy(isInboxEnabled = Override.Unset)`.
     */
    public var configOverrides: RoverConfigOverrides
        get() = BenchSupportOverrideStore.configOverrides
        set(value) {
            BenchSupportOverrideStore.configOverrides = value
        }

    /**
     * Override for the Hub home view experience URL fetched from `GET /home`.
     *
     * [Override.Unset] presents the Hub as though the backend returned no home view, which is
     * a different state from a home view that is disabled in [configOverrides].
     */
    public var homeExperienceURLOverride: Override<String>
        get() = BenchSupportOverrideStore.homeExperienceURLOverride
        set(value) {
            BenchSupportOverrideStore.homeExperienceURLOverride = value
        }

    /**
     * Drops every override, returning the SDK to the backend-provided values. Observers are
     * re-published synchronously.
     */
    public fun clearOverrides() {
        BenchSupportOverrideStore.clear()
    }
}
