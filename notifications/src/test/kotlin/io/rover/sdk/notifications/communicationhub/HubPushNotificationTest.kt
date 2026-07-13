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
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [HubPushNotification] — the single predicate that decides what `PushReceiver` inserts
 * and, via the marker it stamps, what a 410 reset clears. The two sides must agree on what counts
 * as a Hub push, so these cases mirror the payload shapes routed by `PushReceiver` (and iOS
 * `HubPushKindTests`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HubPushNotificationTest {

    @Test
    fun postPayloadIsPost() {
        val payload = JSONObject("""{"post":{"id":"post-1"}}""")
        assertThat(HubPushNotification.kind(payload), equalTo(HubPushKind.POST))
    }

    @Test
    fun conversationPayloadIsConversation() {
        val payload = JSONObject(
            """{"conversation":{"id":"c-1"},"reply":{"id":"r-1"},"participant":{"id":"p-1"}}"""
        )
        assertThat(HubPushNotification.kind(payload), equalTo(HubPushKind.CONVERSATION))
    }

    /**
     * [HubPushNotification.kind] keys only on the `conversation` object. A conversation payload
     * missing its reply/participant siblings is still classified as [HubPushKind.CONVERSATION] so
     * its notification is cleared on reset, even though `PushReceiver` would decline to process it.
     */
    @Test
    fun conversationPayloadWithoutSiblingsIsStillConversation() {
        val payload = JSONObject("""{"conversation":{"id":"c-1"}}""")
        assertThat(HubPushNotification.kind(payload), equalTo(HubPushKind.CONVERSATION))
    }

    @Test
    fun nonHubRoverPayloadIsNull() {
        val payload = JSONObject("""{"notification":{"id":"n-1"}}""")
        assertThat(HubPushNotification.kind(payload), absent())
    }

    @Test
    fun emptyPayloadIsNull() {
        assertThat(HubPushNotification.kind(JSONObject("{}")), absent())
    }

    @Test
    fun stampedNotificationRoundTripsToKind() {
        assertThat(kindAfterStamping(HubPushKind.POST), equalTo(HubPushKind.POST))
        assertThat(kindAfterStamping(HubPushKind.CONVERSATION), equalTo(HubPushKind.CONVERSATION))
    }

    @Test
    fun unmarkedNotificationHasNoKind() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = NotificationCompat.Builder(context, "channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        assertThat(HubPushNotification.kindOf(notification), absent())
    }

    private fun kindAfterStamping(kind: HubPushKind): HubPushKind? {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val builder = NotificationCompat.Builder(context, "channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
        HubPushNotification.stamp(builder, kind)
        return HubPushNotification.kindOf(builder.build())
    }
}
