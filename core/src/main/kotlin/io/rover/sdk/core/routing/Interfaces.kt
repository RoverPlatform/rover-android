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

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.rover.sdk.core.container.Assembler
import java.net.URI

interface Router {
    /**
     * Map the given [uri] to an Intent as per the relevant registered [Route].  If nothing in the
     * currently installed Rover SDK modules can provide an Intent for the URI, returns null.
     *
     * @param inbound This parameter is unused.
     */
    fun route(uri: URI?, inbound: Boolean = false): Intent?

    /**
     * Register the given route.  Should typically be called in [Assembler.afterAssembly]
     * implementations.
     */
    fun registerRoute(route: Route)
}

interface Route {
    /**
     * Return an [Intent] for the given URI if this route is capable of handling it.
     *
     * Note; these do not check the Schema.
     */
    fun resolveUri(uri: URI?): Intent?
}

interface LinkOpenInterface {
    /**
     * If Rover can handle this link, returns an intent that can launch it.
     *
     * Returns null if this link not handled by Rover.
     */
    fun intentForLink(context: Context, uri: Uri): Intent?

    /**
     * Map a URI just received for a deep link to an explicit, mapped intent.
     *
     * May return more than one intent, meant for synthesizing a back stack in the event
     * of the target needing synthesized back stack entries.
     */
    @Deprecated("Use intentForLink() instead.")
    fun localIntentForReceived(receivedUri: URI): List<Intent>
}
