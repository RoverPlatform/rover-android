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

package io.rover.sdk.core.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.routing.LinkOpenInterface
import io.rover.sdk.core.routing.Router
import io.rover.sdk.core.routing.TransientLinkLaunchActivity
import java.net.URI

class LinkOpen(
    private val router: Router,
) : LinkOpenInterface {

    override fun intentForLink(context: Context, uri: Uri): Intent? {
        val javaURI = try { URI(uri.toString()) } catch (e: Throwable) {
            log.e("Unable to parse URI: $uri")
            return null
        }
        return router.route(javaURI)
    }

    @Deprecated("Use intentForLink() instead.")
    override fun localIntentForReceived(receivedUri: URI): List<Intent> {
        return listOfNotNull(router.route(receivedUri))
    }
}
