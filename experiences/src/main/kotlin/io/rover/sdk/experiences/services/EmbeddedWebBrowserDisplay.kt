package io.rover.sdk.experiences.services

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

interface EmbeddedWebBrowserDisplayInterface {
    /**
     * Powered by https://developer.chrome.com/multidevice/android/customtabs, this allows
     * you to do an in-band embedded web browser view that allows you to keep your brand
     * colour and return easily to application flow.
     *
     * While it is branded Chrome, it can also be provided by other browsers on the users' devices,
     * such as Firefox.
     */
    fun intentForViewingWebsiteViaEmbeddedBrowser(url: String): Intent
}

internal class EmbeddedWebBrowserDisplay(
    /**
     * Set the background colour for the Chrome Custom tab's title bar.
     *
     * Consider using your theme's primary or accent colour.
     */
    private val backgroundColor: Int = Color.BLACK
) : EmbeddedWebBrowserDisplayInterface {
    override fun intentForViewingWebsiteViaEmbeddedBrowser(url: String): Intent {
        val builder = CustomTabsIntent.Builder()

        builder.setToolbarColor(backgroundColor)
        val customTabIntentHolder = builder.build()
        customTabIntentHolder.intent.data = Uri.parse(url)
        return customTabIntentHolder.intent
    }
}