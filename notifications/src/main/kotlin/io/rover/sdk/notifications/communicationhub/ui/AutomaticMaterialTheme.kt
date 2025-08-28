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

package io.rover.sdk.notifications.communicationhub.ui


import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import io.rover.sdk.notifications.materialcolor.internal.hct.Hct
import io.rover.sdk.notifications.materialcolor.internal.scheme.SchemeTonalSpot


/**
 * Automatically (using material-colors-utilities) generates a Material 3 color scheme based on the
 * provided source color, and then applies it to a MaterialTheme.
 */
@Composable
internal fun automaticMaterialScheme(
    sourceColor: Color,
    isDark: Boolean,
    // Dynamic color is available on Android 12+.
    dynamicColor: Boolean = false
): ColorScheme {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        // otherwise, use our generated color scheme
        isDark -> generateDarkScheme(sourceColor, contrastLevel = 0.0)
        else -> generateLightScheme(sourceColor, contrastLevel = 0.0)
    }

    return colorScheme
}

@Composable
private fun generateLightScheme(
    sourceColor: Color,
    contrastLevel: Double = 0.0,
): ColorScheme {
    val dynamicScheme =
        remember { SchemeTonalSpot(Hct.fromInt(sourceColor.toArgb()), false, contrastLevel) }

    val colorScheme = remember {
        lightColorScheme(
            primary = Color(dynamicScheme.primary),
            onPrimary = Color(dynamicScheme.onPrimary),
            primaryContainer = Color(dynamicScheme.primaryContainer),
            onPrimaryContainer = Color(dynamicScheme.onPrimaryContainer),
            secondary = Color(dynamicScheme.secondary),
            onSecondary = Color(dynamicScheme.onSecondary),
            secondaryContainer = Color(dynamicScheme.secondaryContainer),
            onSecondaryContainer = Color(dynamicScheme.onSecondaryContainer),
            tertiary = Color(dynamicScheme.tertiary),
            onTertiary = Color(dynamicScheme.onTertiary),
            tertiaryContainer = Color(dynamicScheme.tertiaryContainer),
            onTertiaryContainer = Color(dynamicScheme.onTertiaryContainer),
            error = Color(dynamicScheme.error),
            onError = Color(dynamicScheme.onError),
            errorContainer = Color(dynamicScheme.errorContainer),
            onErrorContainer = Color(dynamicScheme.onErrorContainer),
            background = Color(dynamicScheme.background),
            onBackground = Color(dynamicScheme.onBackground),
            surface = Color(dynamicScheme.surface),
            onSurface = Color(dynamicScheme.onSurface),
            surfaceVariant = Color(dynamicScheme.surfaceVariant),
            onSurfaceVariant = Color(dynamicScheme.onSurfaceVariant),
            outline = Color(dynamicScheme.outline),
            outlineVariant = Color(dynamicScheme.outlineVariant),
            scrim = Color(dynamicScheme.scrim),
            inverseSurface = Color(dynamicScheme.inverseSurface),
            inverseOnSurface = Color(dynamicScheme.inverseOnSurface),
            inversePrimary = Color(dynamicScheme.inversePrimary),
            surfaceDim = Color(dynamicScheme.surfaceDim),
            surfaceBright = Color(dynamicScheme.surfaceBright),
            surfaceContainerLowest = Color(dynamicScheme.surfaceContainerLowest),
            surfaceContainerLow = Color(dynamicScheme.surfaceContainerLow),
            surfaceContainer = Color(dynamicScheme.surfaceContainer),
            surfaceContainerHigh = Color(dynamicScheme.surfaceContainerHigh),
            surfaceContainerHighest = Color(dynamicScheme.surfaceContainerHighest),
            surfaceTint = Color(dynamicScheme.surfaceTint),
//            primaryFixed = Color(dynamicScheme.primaryFixed),
//            onPrimaryFixed = Color(dynamicScheme.onPrimaryFixed),
//            primaryFixedDim = Color(dynamicScheme.primaryFixedDim),
//            onPrimaryFixedVariant = Color(dynamicScheme.onPrimaryFixedVariant),
//            secondaryFixedDim = Color(dynamicScheme.secondaryFixedDim),
//            secondaryFixed = Color(dynamicScheme.secondaryFixed),
//            onSecondaryFixed = Color(dynamicScheme.onSecondaryFixed),
//            onSecondaryFixedVariant = Color(dynamicScheme.onSecondaryFixedVariant),
//            tertiaryFixedDim = Color(dynamicScheme.tertiaryFixedDim),
//            tertiaryFixed = Color(dynamicScheme.tertiaryFixed),
//            onTertiaryFixed = Color(dynamicScheme.onTertiaryFixed),
//            onTertiaryFixedVariant = Color(dynamicScheme.onTertiaryFixedVariant),
        )
    }

    return colorScheme
}


@Composable
private fun generateDarkScheme(
    sourceColor: Color,
    contrastLevel: Double = 0.0,
): ColorScheme {
    val dynamicScheme =
        remember { SchemeTonalSpot(Hct.fromInt(sourceColor.toArgb()), true, contrastLevel) }

    val colorScheme = remember {
        darkColorScheme(
            primary = Color(dynamicScheme.primary),
            onPrimary = Color(dynamicScheme.onPrimary),
            primaryContainer = Color(dynamicScheme.primaryContainer),
            onPrimaryContainer = Color(dynamicScheme.onPrimaryContainer),
            secondary = Color(dynamicScheme.secondary),
            onSecondary = Color(dynamicScheme.onSecondary),
            secondaryContainer = Color(dynamicScheme.secondaryContainer),
            onSecondaryContainer = Color(dynamicScheme.onSecondaryContainer),
            tertiary = Color(dynamicScheme.tertiary),
            onTertiary = Color(dynamicScheme.onTertiary),
            tertiaryContainer = Color(dynamicScheme.tertiaryContainer),
            onTertiaryContainer = Color(dynamicScheme.onTertiaryContainer),
            error = Color(dynamicScheme.error),
            onError = Color(dynamicScheme.onError),
            errorContainer = Color(dynamicScheme.errorContainer),
            onErrorContainer = Color(dynamicScheme.onErrorContainer),
            background = Color(dynamicScheme.background),
            onBackground = Color(dynamicScheme.onBackground),
            surface = Color(dynamicScheme.surface),
            onSurface = Color(dynamicScheme.onSurface),
            surfaceVariant = Color(dynamicScheme.surfaceVariant),
            onSurfaceVariant = Color(dynamicScheme.onSurfaceVariant),
            outline = Color(dynamicScheme.outline),
            outlineVariant = Color(dynamicScheme.outlineVariant),
            scrim = Color(dynamicScheme.scrim),
            inverseSurface = Color(dynamicScheme.inverseSurface),
            inverseOnSurface = Color(dynamicScheme.inverseOnSurface),
            inversePrimary = Color(dynamicScheme.inversePrimary),
            surfaceDim = Color(dynamicScheme.surfaceDim),
            surfaceBright = Color(dynamicScheme.surfaceBright),
            surfaceContainerLowest = Color(dynamicScheme.surfaceContainerLowest),
            surfaceContainerLow = Color(dynamicScheme.surfaceContainerLow),
            surfaceContainer = Color(dynamicScheme.surfaceContainer),
            surfaceContainerHigh = Color(dynamicScheme.surfaceContainerHigh),
            surfaceContainerHighest = Color(dynamicScheme.surfaceContainerHighest),
            surfaceTint = Color(dynamicScheme.surfaceTint),
//            primaryFixed = Color(dynamicScheme.primaryFixed),
//            onPrimaryFixed = Color(dynamicScheme.onPrimaryFixed),
//            primaryFixedDim = Color(dynamicScheme.primaryFixedDim),
//            onPrimaryFixedVariant = Color(dynamicScheme.onPrimaryFixedVariant),
//            secondaryFixedDim = Color(dynamicScheme.secondaryFixedDim),
//            secondaryFixed = Color(dynamicScheme.secondaryFixed),
//            onSecondaryFixed = Color(dynamicScheme.onSecondaryFixed),
//            onSecondaryFixedVariant = Color(dynamicScheme.onSecondaryFixedVariant),
//            tertiaryFixedDim = Color(dynamicScheme.tertiaryFixedDim),
//            tertiaryFixed = Color(dynamicScheme.tertiaryFixed),
//            onTertiaryFixed = Color(dynamicScheme.onTertiaryFixed),
//            onTertiaryFixedVariant = Color(dynamicScheme.onTertiaryFixedVariant),
        )
    }

    return colorScheme
}
