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

import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.core.os.bundleOf
import org.json.JSONObject

/**
 * The kind of Hub content a push carries, or absent if it is not a Hub push.
 */
internal enum class HubPushKind {
    POST,
    CONVERSATION,
}

/**
 * Single source of truth for "is this a Hub push?" on Android.
 *
 * Mirrors iOS `InboxPersistentContainer.hubPushKind(from:)`. It is used on two sides that must
 * agree, or a stale, tappable notification can survive a 410 reset:
 *
 * - **Ingestion** ([kind]): `PushReceiver` classifies the parsed `rover` payload to decide what to
 *   insert, and to decide which marker to [stamp] on the notification it posts.
 * - **410 reset clearing** ([kindOf]): `HubSyncCoordinator` reads that marker back off already
 *   delivered notifications to decide which to cancel.
 *
 * Unlike iOS, an Android `StatusBarNotification` does not retain the FCM payload, and every Rover
 * notification (classic campaign, post, conversation) shares the same integer notification id — so
 * the reset cannot re-parse the payload or select by id. Instead the payload classification is
 * recorded as an extra at post time and matched at reset time. The classification itself
 * ([kind]) remains the one definition both sides derive from.
 */
internal object HubPushNotification {

    /**
     * Extra key stamped on a delivered Hub notification recording its [HubPushKind].
     *
     * Lets a 410 reset identify and cancel Hub notifications (posts and conversations) without the
     * original FCM payload and without cross-referencing Room, while leaving classic/deep-link
     * notifications (which carry no marker) untouched.
     */
    const val EXTRA_HUB_PUSH_KIND: String = "io.rover.sdk.notifications.HUB_PUSH_KIND"

    /**
     * Classifies a parsed `rover` push payload.
     *
     * Keys conversation on the presence of the `conversation` object alone (not its
     * reply/participant siblings), matching iOS, so a malformed conversation payload is still
     * classified as [HubPushKind.CONVERSATION] and its notification is cleared on reset even though
     * `PushReceiver` would decline to process it. Returns `null` for non-Hub payloads such as
     * classic campaign notifications.
     */
    fun kind(payload: JSONObject): HubPushKind? = when {
        payload.optJSONObject("post") != null -> HubPushKind.POST
        payload.optJSONObject("conversation") != null -> HubPushKind.CONVERSATION
        else -> null
    }

    /**
     * Records [kind] on [builder] so a later 410 reset can identify this notification as a Hub push.
     */
    fun stamp(builder: NotificationCompat.Builder, kind: HubPushKind) {
        builder.addExtras(bundleOf(EXTRA_HUB_PUSH_KIND to kind.name))
    }

    /**
     * Reads the [HubPushKind] marker previously [stamp]ed on a delivered notification, or `null` if
     * it carries no Hub marker (e.g. a classic campaign or deep-link notification).
     */
    fun kindOf(notification: Notification): HubPushKind? {
        val name = notification.extras?.getString(EXTRA_HUB_PUSH_KIND) ?: return null
        return runCatching { HubPushKind.valueOf(name) }.getOrNull()
    }
}
