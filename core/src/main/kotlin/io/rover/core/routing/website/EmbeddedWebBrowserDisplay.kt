package io.rover.core.routing.website

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.support.customtabs.CustomTabsIntent

class EmbeddedWebBrowserDisplay(
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
