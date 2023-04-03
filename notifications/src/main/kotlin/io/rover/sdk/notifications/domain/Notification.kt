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

package io.rover.sdk.notifications.domain

import io.rover.sdk.notifications.NotificationsAssembler
import java.net.URI
import java.net.URL
import java.util.Date

/**
 * Rover push notifications have this format.
 *
 * Note that this is used not only in the GraphQL API but also in the push notification payloads
 * delivered over the push platform (typically FCM).
 *
 * When received from Firebase, they are delivered as a JSON-encoded object set as the `message` key
 * on the Firebase `RemoteMessage`.
 *
 */
data class Notification(
    val id: String,

    /**
     * An Android channel ID.  If not set, Rover will use the default channel id set in (see
     * [NotificationsAssembler]).
     */
    val channelId: String?,

    val title: String?,
    val body: String,

    /**
     * Has this notification been read?
     *
     * (when received over push this will always be false)
     */
    val isRead: Boolean,

    /**
     * Has this notification been deleted?
     *
     * When received over push will always be false.
     *
     * However, note that, for now, this will always be false when being returned from the device
     * state GraphQL API as well, although the API reserves the possibility of setting it to be
     * true in the future.
     */
    val isDeleted: Boolean,

    val isNotificationCenterEnabled: Boolean,

    val expiresAt: Date?,

    val deliveredAt: Date,

    val tapBehavior: TapBehavior,

    val attachment: NotificationAttachment?,

    val campaignId: String,

    val conversionTags: List<String>?
) {
    sealed class TapBehavior {
        data class PresentWebsite(val url: URI) : TapBehavior()
        data class OpenUri(val uri: URI) : TapBehavior()
        class OpenApp : TapBehavior()

        companion object
    }

    companion object
}

sealed class NotificationAttachment(
    val typeName: String,
    val url: URL
) {
    class Audio(url: URL) : NotificationAttachment("audio", url)
    class Image(url: URL) : NotificationAttachment("image", url)
    class Video(url: URL) : NotificationAttachment("video", url)

    override fun toString(): String {
        return "NotificationAttachment(typeName=$typeName, url=$url)"
    }

    companion object
}
