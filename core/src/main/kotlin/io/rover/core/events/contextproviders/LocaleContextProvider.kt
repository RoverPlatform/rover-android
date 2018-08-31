package io.rover.core.events.contextproviders

import android.content.res.Resources
import android.os.Build
import io.rover.core.data.domain.DeviceContext
import io.rover.core.events.ContextProvider
import java.util.Locale

/**
 * Captures and adds the user's locale information from [resources] and adds it to [DeviceContext].
 */
class LocaleContextProvider(
    private val resources: Resources
) : ContextProvider {

    private fun getLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale
        }
    }

    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        val locale = getLocale()
        return deviceContext.copy(
            // ISO 639 alpha-2.
            localeLanguage = locale.language,

            // ISO 15924 alpha-4.
            localeScript = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                locale.script
            } else {
                // no straight-forward way to get the locale Script on older Android, alas.
                null
            },

            // ISO 3166 alpha-2.
            localeRegion = locale.country
        )
    }
}
