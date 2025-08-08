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

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import io.rover.sdk.core.routing.LinkOpenInterface

/**
 * Before opening the link with the Rover Route (LinkOpen), attempt to use
 * a Chrome Custom Tab if the URL is a web link.
 *
 * This should be used for when opening links from within Rover UI.
 */
internal fun LinkOpenInterface.openLink(url: String, context: Context) {

    val uri = url.toUri()
    // give rover router first crack at the URL
    val intent = intentForLink(context, uri) ?: {
        // If Rover Router doesn't handle it, check if it's a web URL
        val scheme = uri.scheme?.lowercase()

        if (scheme == "http" || scheme == "https") {
            // Web URL - use Custom Tabs
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .intent.apply {
                    data = uri
                }
        } else {
            // // for custom deep links, mailto:, or other non-web schemes, make a standard intent
            Intent(Intent.ACTION_VIEW, uri)
        }
    }()
    context.startActivity(intent)
}
