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

package io.rover.sdk.core.routing.website

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

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
