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

package io.rover.sdk.notifications.routing.routes

import android.content.Intent
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.routing.Route
import java.net.URI

class PresentNotificationCenterRoute(
    private val urlSchemes: List<String>,
    private val notificationCenterIntent: Intent?
) : Route {
    override fun resolveUri(uri: URI?): Intent? {
        return if (urlSchemes.contains(uri?.scheme) && uri?.authority == "presentNotificationCenter") {
            notificationCenterIntent.apply {
                if (this == null) log.w("Notification Open intent needed, but one was not specified to NotificationsAssembler.")
            }
        } else null
    }
}
