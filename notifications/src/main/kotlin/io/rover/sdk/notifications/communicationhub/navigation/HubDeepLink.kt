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

package io.rover.sdk.notifications.communicationhub.navigation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import io.rover.sdk.core.data.config.ConfigManager
import io.rover.sdk.core.logging.log

/**
 * The host app's Hub deep link, as configured on the Rover dashboard.
 *
 * Hub coordination hands the host app an `ACTION_VIEW` intent asking it to reveal its Hub tab,
 * having first queued the destination on [HubCoordinator]. Because the deep link is remotely
 * configured free text rather than something the SDK controls, an unset, blank, or mismatched
 * value must degrade to the SDK's own standalone UI. Dispatching an intent nothing can answer
 * ends the notification tap on an empty task, which the user sees as the app opening and then
 * immediately closing by itself.
 */
internal object HubDeepLink {
    /**
     * Builds the intent that asks the host app to reveal its Hub, or null when the caller should
     * present the SDK's standalone UI instead. The deep link needs to be handled by the host app.
     */
    fun revealIntent(configManager: ConfigManager, context: Context): Intent? {
        val config = configManager.config.value

        if (!config.hub.isInboxEnabled) {
            return null
        }

        val deepLink = config.hub.deepLink?.takeIf { it.isNotBlank() } ?: return null

        // Constrain the intent to the host app before checking it. Without this the gate could
        // find the host app's handler while startActivity still resolved to another app that is
        // the user's default for the scheme, sending the user elsewhere with navigation queued.
        val intent = Intent(Intent.ACTION_VIEW, deepLink.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(context.packageName)
        }

        val handledByThisApp = context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .isNotEmpty()

        if (!handledByThisApp) {
            log.w(
                "This app declares no activity that handles the configured Hub deep link '$deepLink', so the Hub cannot be revealed.\n" +
                    "Check that the deep link set on the Rover dashboard matches an intent filter in the host app's manifest.\n" +
                    "Falling back to the SDK's standalone UI."
            )
            return null
        }

        return intent
    }
}
