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

package io.rover.sdk.core.routing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.rover.sdk.core.Rover
import io.rover.sdk.core.logging.log
import java.net.URI

/**
 * The intent filter you add for your universal links and `rv-$appname://` deep links should by default
 * point to this activity.
 */
open class TransientLinkLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)



        val rover = Rover.failableShared
        if (rover == null) {
            log.e("A deep or universal link mapped to Rover was opened, but Rover is not initialized.  Ignoring.")
            finish()
            return
        }

        // if for some reason we can't resolve a useful intent, fallback to just opening the app.

        val fallbackIntent = rover.resolve(Intent::class.java, "openApp")
        val linkOpen = rover.resolve(LinkOpenInterface::class.java)
        if (linkOpen == null) {
            log.e("A deep or universal link mapped to Rover was opened, but LinkOpenInterface is not registered in the Rover container. Ensure ExperiencesAssembler() is in Rover.initialize(). Ignoring.")
            finish()
            return
        }

        val uri = intent.data ?: run {
            log.e("Transient link launch activity was launched, but no URI was provided.  Ignoring.")
            finish()
            return
        }

        log.v("Transient link launch activity running for received URI: '${intent.data}'")

        // The router stops at the first matching Route and isolates one that throws, so a
        // routing failure should no longer escape it. This guard stays as a second line of
        // defence: this activity is an entry point from outside the app, so anything unexpected
        // escaping the routing layer would take the process down on a link tap. A plain
        // ACTION_VIEW for a Rover link also resolves to this activity, so a caller that treats a
        // routing failure as "no route" and falls back to ACTION_VIEW ends up routing the same URI
        // again in here. Treat a failure as unroutable and use the app open fallback below.
        val routedIntent = try {
            linkOpen.intentForLink(this, uri)
        } catch (exception: Exception) {
            log.w(
                "Could not route the inbound link '$uri', treating it as unroutable: " +
                    "$exception, at ${exception.stackTraceToString()}"
            )
            null
        }

        val linkIntent = routedIntent ?: run {
            // no fallback to making a stock ACTION_VIEW intent for this uri is needed if Rover
            // Router fails to produce a Uri is needed. This is because Link Launch activity
            // is only used for *inbound* links being launched with the Rover SDK.
            log.w("Rover router could not give us a URL for the URI: '$uri'.  Ignoring.")

            // fall back to app open intent so there's not a blank screen
            fallbackIntent ?: run {
                finish()
                return
            }
        }

        log.v("Launching intent: $linkIntent")

        // The routed intent can be an ACTION_VIEW for a remotely configured deep link, so nothing
        // guarantees an installed activity can answer it. Open the app rather than taking the
        // host app's process down on an inbound link. linkIntent may already BE fallbackIntent,
        // when routing produced nothing above; the helper knows not to relaunch it.
        startActivityOrOpenApp(
            intent = linkIntent,
            openApp = fallbackIntent,
            failureMessage = "No activity could be found to handle the intent produced for this link.\n" +
                "This could be because a deep link configured on the Rover dashboard does not match any intent filter in the app's manifest.",
        )

        finish()
    }

    companion object {
        /**
         * To programmatically launch a link with transient link launch activity, use this method to create an Intent.
         */
        fun makeIntent(context: Context, uri: URI): Intent {
            return Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString()), context, TransientLinkLaunchActivity::class.java)
        }
    }
}
