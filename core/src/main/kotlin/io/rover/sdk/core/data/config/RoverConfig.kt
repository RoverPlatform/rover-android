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

package io.rover.sdk.core.data.config

import com.squareup.moshi.JsonClass

/**
 * Hub-specific configuration nested under the hub object in the backend API.
 *
 * @property isHomeEnabled Whether the home view is enabled
 * @property isInboxEnabled Whether the inbox is enabled
 * @property isSettingsViewEnabled Whether the settings view is enabled
 * @property deepLink Optional deep link for revealing the hub in the client's app.
 */
@JsonClass(generateAdapter = true)
data class HubConfig(
    val isHomeEnabled: Boolean = false,
    val isInboxEnabled: Boolean = true,
    val isSettingsViewEnabled: Boolean = false,
    val deepLink: String? = null
)

/**
 * Remotely configurable configuration for Rover, including Hub features and appearance.
 *
 * This data class defines the configuration options available for Hub, including
 * feature flags, UI customization, and navigation settings. The configuration can be provided
 * by the backend API or overridden locally for development/testing purposes.
 *
 * As of the backend API update in commit cafabdceb96519ea298e580763fbc708a236227a,
 * hub-related configuration properties are nested under a `hub` object with renamed properties.
 *
 * @property hub Hub-specific configuration
 * @property colorScheme Color scheme preference (dark, light, or auto)
 * @property accentColor Optional accent color for theming
 *
 * @see ConfigManager for managing configuration sources and override mode
 */
@JsonClass(generateAdapter = true)
public data class RoverConfig(
    val hub: HubConfig = HubConfig(),
    val colorScheme: CommHubColorScheme? = null,
    val accentColor: String? = null
)
