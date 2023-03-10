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
import androidx.core.content.ContextCompat
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
            return
        }
        val linkOpen = rover.resolve(LinkOpenInterface::class.java)
        if (linkOpen == null) {
            log.e("A deep or universal link mapped to Rover was opened, but LinkOpenInterface is not registered in the Rover container. Ensure ExperiencesAssembler() is in Rover.initialize(). Ignoring.")
            return
        }

        val uri = URI(intent.data.toString())

        log.v("Transient link launch activity running for received URI: '${intent.data}'")

        val intentStack = linkOpen.localIntentForReceived(uri)

        log.v("Launching stack ${intentStack.size} deep: ${intentStack.joinToString("\n") { it.toString() }}")

        if (intentStack.isNotEmpty()) ContextCompat.startActivities(this, intentStack.toTypedArray())
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
