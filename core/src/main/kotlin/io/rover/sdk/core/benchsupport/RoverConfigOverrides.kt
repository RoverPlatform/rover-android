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

import io.rover.sdk.core.data.config.CommHubColorScheme
import io.rover.sdk.core.data.config.HubConfig
import io.rover.sdk.core.data.config.RoverConfig

/**
 * Local overrides layered over the backend-provided [RoverConfig].
 *
 * Every field defaults to [Override.NoOverride], so a default instance is a no-op. Set only
 * the fields the bench needs to pin; the rest keep tracking the backend.
 *
 * @property isHomeEnabled Overrides `hub.isHomeEnabled`. [Override.Unset] resolves to the
 *   [HubConfig] default.
 * @property isInboxEnabled Overrides `hub.isInboxEnabled`. [Override.Unset] resolves to the
 *   [HubConfig] default.
 * @property hubDeepLink Overrides `hub.deepLink`. [Override.Unset] removes the deep link,
 *   which is how the bench selects a modally-presented Hub.
 * @property colorScheme Overrides `colorScheme`. [Override.Unset] removes the backend's
 *   preference; note that [CommHubColorScheme.AUTO] is a value, not an absence.
 * @property accentColor Overrides `accentColor`, a hex string.
 *
 * @see RoverBenchSupport
 */
@RoverBenchSupportApi
public data class RoverConfigOverrides(
    val isHomeEnabled: Override<Boolean> = Override.NoOverride,
    val isInboxEnabled: Override<Boolean> = Override.NoOverride,
    val hubDeepLink: Override<String> = Override.NoOverride,
    val colorScheme: Override<CommHubColorScheme> = Override.NoOverride,
    val accentColor: Override<String> = Override.NoOverride
) {
    internal companion object {
        /**
         * The field defaults declared by [HubConfig], which [Override.Unset] resolves to for
         * the non-nullable hub flags.
         */
        private val HUB_DEFAULTS = HubConfig()
    }

    /**
     * Returns [config] with these overrides applied on top.
     */
    internal fun applyTo(config: RoverConfig): RoverConfig = config.copy(
        hub = config.hub.copy(
            isHomeEnabled = isHomeEnabled.resolve(
                config.hub.isHomeEnabled,
                HUB_DEFAULTS.isHomeEnabled
            ),
            isInboxEnabled = isInboxEnabled.resolve(
                config.hub.isInboxEnabled,
                HUB_DEFAULTS.isInboxEnabled
            ),
            deepLink = hubDeepLink.resolve(config.hub.deepLink)
        ),
        colorScheme = colorScheme.resolve(config.colorScheme),
        accentColor = accentColor.resolve(config.accentColor)
    )
}
