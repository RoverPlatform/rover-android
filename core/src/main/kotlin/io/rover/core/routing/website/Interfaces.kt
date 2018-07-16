package io.rover.core.routing.website

import android.content.Intent

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
