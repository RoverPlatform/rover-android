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

package io.rover.sdk.notifications.communicationhub

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.rover.sdk.core.Rover
import io.rover.sdk.core.data.config.CommHubColorScheme
import io.rover.sdk.core.roverConfig

/**
 * Resolves whether Hub surfaces should render dark from the remote [RoverConfig] colorScheme:
 * DARK/LIGHT force the appearance, AUTO (or an absent value) follows the device via
 * [isSystemInDarkTheme]. Shared by the embedded Hub, the standalone Post/Conversation activities,
 * and the Post detail WebView so the forced scheme applies consistently to native chrome and web
 * content on every presentation path.
 */
@Composable
internal fun rememberCommHubDarkTheme(): Boolean {
    val config by Rover.shared.roverConfig.collectAsState()
    return when (config.colorScheme) {
        CommHubColorScheme.DARK -> true
        CommHubColorScheme.LIGHT -> false
        CommHubColorScheme.AUTO, null -> isSystemInDarkTheme()
    }
}

/**
 * Builds a deliberately neutral Material 3 color scheme for Hub surfaces, with the configured
 * accent exposed as [ColorScheme.primary].
 */
@Composable
internal fun automaticMaterialScheme(
    sourceColor: Color,
    isDark: Boolean,
    dynamicColor: Boolean = false,
): ColorScheme = remember(sourceColor, isDark, dynamicColor) {
    if (isDark) {
        darkHubColorScheme(sourceColor)
    } else {
        lightHubColorScheme(sourceColor)
    }
}

private fun lightHubColorScheme(accentColor: Color): ColorScheme =
    lightColorScheme(
        primary = accentColor,
        onPrimary = contentColorFor(accentColor),
        primaryContainer = accentColor,
        onPrimaryContainer = contentColorFor(accentColor),
        secondary = Light.secondary,
        onSecondary = Light.onSecondary,
        secondaryContainer = Light.surfaceContainerHigh,
        onSecondaryContainer = Light.onSurface,
        tertiary = Light.tertiary,
        onTertiary = Light.onTertiary,
        tertiaryContainer = Light.surfaceContainerHigh,
        onTertiaryContainer = Light.onSurface,
        error = Light.error,
        onError = Light.onError,
        errorContainer = Light.errorContainer,
        onErrorContainer = Light.onErrorContainer,
        background = Light.background,
        onBackground = Light.onSurface,
        surface = Light.surface,
        onSurface = Light.onSurface,
        surfaceVariant = Light.surfaceVariant,
        onSurfaceVariant = Light.onSurfaceVariant,
        outline = Light.outline,
        outlineVariant = Light.outlineVariant,
        scrim = Scrim,
        inverseSurface = Light.inverseSurface,
        inverseOnSurface = Light.inverseOnSurface,
        inversePrimary = accentColor,
        surfaceDim = Light.surfaceDim,
        surfaceBright = Light.surfaceBright,
        surfaceContainerLowest = Light.surfaceContainerLowest,
        surfaceContainerLow = Light.surfaceContainerLow,
        surfaceContainer = Light.surfaceContainer,
        surfaceContainerHigh = Light.surfaceContainerHigh,
        surfaceContainerHighest = Light.surfaceContainerHighest,
        surfaceTint = Color.Transparent,
    )

private fun darkHubColorScheme(accentColor: Color): ColorScheme =
    darkColorScheme(
        primary = accentColor,
        onPrimary = contentColorFor(accentColor),
        primaryContainer = accentColor,
        onPrimaryContainer = contentColorFor(accentColor),
        secondary = Dark.secondary,
        onSecondary = Dark.onSecondary,
        secondaryContainer = Dark.surfaceContainerHigh,
        onSecondaryContainer = Dark.onSurface,
        tertiary = Dark.tertiary,
        onTertiary = Dark.onTertiary,
        tertiaryContainer = Dark.surfaceContainerHigh,
        onTertiaryContainer = Dark.onSurface,
        error = Dark.error,
        onError = Dark.onError,
        errorContainer = Dark.errorContainer,
        onErrorContainer = Dark.onErrorContainer,
        background = Dark.background,
        onBackground = Dark.onSurface,
        surface = Dark.surface,
        onSurface = Dark.onSurface,
        surfaceVariant = Dark.surfaceVariant,
        onSurfaceVariant = Dark.onSurfaceVariant,
        outline = Dark.outline,
        outlineVariant = Dark.outlineVariant,
        scrim = Scrim,
        inverseSurface = Dark.inverseSurface,
        inverseOnSurface = Dark.inverseOnSurface,
        inversePrimary = accentColor,
        surfaceDim = Dark.surfaceDim,
        surfaceBright = Dark.surfaceBright,
        surfaceContainerLowest = Dark.surfaceContainerLowest,
        surfaceContainerLow = Dark.surfaceContainerLow,
        surfaceContainer = Dark.surfaceContainer,
        surfaceContainerHigh = Dark.surfaceContainerHigh,
        surfaceContainerHighest = Dark.surfaceContainerHighest,
        surfaceTint = Color.Transparent,
    )

internal fun contentColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White

private val Scrim = Color.Black

// Tailwind Zinc is a slightly cool violet-gray palette, matching iOS system grays' subtle blue
// cast more closely than neutral or stone while keeping the Android theme platform-independent.
// Light and dark appearances use paired stops from the same scale so neutral surfaces invert cleanly.
private object Zinc {
    val `50` = Color(0xFFFAFAFA)
    val `100` = Color(0xFFF4F4F5)
    val `200` = Color(0xFFE4E4E7)
    val `300` = Color(0xFFD4D4D8)
    val `400` = Color(0xFFA1A1AA)
    val `500` = Color(0xFF71717A)
    val `700` = Color(0xFF3F3F46)
    val `800` = Color(0xFF27272A)
    val `900` = Color(0xFF18181B)
}

private object Light {
    val secondary = Zinc.`700`
    val onSecondary = Color.White
    val tertiary = Zinc.`700`
    val onTertiary = Color.White
    val error = Color(0xFFB00020)
    val onError = Color.White
    val errorContainer = Color(0xFFF9DEDC)
    val onErrorContainer = Color(0xFF410E0B)
    val background = Color.White
    val surface = Color.White
    val surfaceVariant = Zinc.`200`
    val onSurface = Zinc.`900`
    val onSurfaceVariant = Zinc.`500`
    val outline = Zinc.`400`
    val outlineVariant = Zinc.`300`
    val inverseSurface = Zinc.`900`
    val inverseOnSurface = Color.White
    val surfaceDim = Zinc.`100`
    val surfaceBright = Color.White
    val surfaceContainerLowest = Color.White
    val surfaceContainerLow = Zinc.`50`
    val surfaceContainer = Zinc.`100`
    val surfaceContainerHigh = Zinc.`200`
    val surfaceContainerHighest = Zinc.`200`
}

private object Dark {
    val secondary = Zinc.`200`
    val onSecondary = Color.Black
    val tertiary = Zinc.`200`
    val onTertiary = Color.Black
    val error = Color(0xFFFFB4AB)
    val onError = Color(0xFF690005)
    val errorContainer = Color(0xFF93000A)
    val onErrorContainer = Color(0xFFFFDAD6)
    val background = Color.Black
    val surface = Color.Black
    val surfaceVariant = Zinc.`800`
    val onSurface = Zinc.`100`
    val onSurfaceVariant = Zinc.`400`
    val outline = Zinc.`500`
    val outlineVariant = Zinc.`700`
    val inverseSurface = Zinc.`200`
    val inverseOnSurface = Color.Black
    val surfaceDim = Color.Black
    val surfaceBright = Zinc.`800`
    val surfaceContainerLowest = Color.Black
    val surfaceContainerLow = Zinc.`900`
    val surfaceContainer = Zinc.`900`
    val surfaceContainerHigh = Zinc.`800`
    val surfaceContainerHighest = Zinc.`700`
}
