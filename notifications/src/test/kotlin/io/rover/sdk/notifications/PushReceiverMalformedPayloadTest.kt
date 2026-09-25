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

package io.rover.sdk.notifications

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.core.events.PushTokenTransmissionChannel
import io.rover.sdk.core.logging.GlobalStaticLogHolder
import io.rover.sdk.core.logging.LogEmitter
import io.rover.sdk.core.platform.DateFormatting
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [PushReceiver.onMessageReceivedData] is called straight from the host's FirebaseMessagingService,
 * so anything it throws lands in host code. These pin two properties of a malformed `rover`
 * payload: it never throws out of the receiver, and none of its contents reach the log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PushReceiverMalformedPayloadTest {
    private val capturedLog = mutableListOf<String>()
    private var previousEmitter: LogEmitter? = null

    @Before
    fun captureLog() {
        previousEmitter = GlobalStaticLogHolder.globalLogEmitter
        GlobalStaticLogHolder.globalLogEmitter = object : LogEmitter {
            override fun e(logTag: String, message: String) { capturedLog += message }
            override fun w(logTag: String, message: String) { capturedLog += message }
            override fun v(logTag: String, message: String) { capturedLog += message }
            override fun i(logTag: String, message: String) { capturedLog += message }
            override fun d(logTag: String, message: String) { capturedLog += message }
        }
    }

    @After
    fun restoreLog() {
        GlobalStaticLogHolder.globalLogEmitter = previousEmitter
    }

    private fun makeReceiver(notificationDispatcher: NotificationDispatcher): PushReceiver {
        return PushReceiver(
            pushTokenTransmissionChannel = mock<PushTokenTransmissionChannel>(),
            notificationDispatcher = notificationDispatcher,
            dateFormatting = DateFormatting(),
            influenceTrackerService = mock<InfluenceTrackerServiceInterface>(),
            postPushHandler = null,
            conversationPushHandler = null,
        )
    }

    /**
     * A complete legacy notification, with any field overridden by [overrides] (raw JSON values).
     */
    private fun legacyNotification(vararg overrides: Pair<String, String>): String {
        val fields = linkedMapOf(
            "id" to "\"notification-1\"",
            "campaignID" to "\"campaign-1\"",
            "title" to "\"Title\"",
            "body" to "\"Body\"",
            "isRead" to "false",
            "isDeleted" to "false",
            "isNotificationCenterEnabled" to "false",
            "deliveredAt" to "\"2026-08-31T09:00:00.000Z\"",
            "tapBehavior" to """{ "__typename": "OpenAppNotificationTapBehavior" }""",
        )
        overrides.forEach { (name, value) -> fields[name] = value }
        val body = fields.entries.joinToString(",\n") { (name, value) -> "\"$name\": $value" }
        return """{ "notification": { $body } }"""
    }

    private fun assertSkippedWithoutDispatch(roverJson: String) {
        val notificationDispatcher = mock<NotificationDispatcher>()
        val receiver = makeReceiver(notificationDispatcher)

        receiver.onMessageReceivedData(mapOf("rover" to roverJson))

        verify(notificationDispatcher, never()).ingest(any(), anyOrNull())
    }

    @Test
    fun aNotificationThatIsNotAnObjectIsSkipped() {
        assertSkippedWithoutDispatch("""{"notification": "not-an-object"}""")
    }

    @Test
    fun aNotificationMissingRequiredFieldsIsSkipped() {
        assertSkippedWithoutDispatch("""{"notification": {}}""")
    }

    @Test
    fun anUnknownTapBehaviorIsSkipped() {
        assertSkippedWithoutDispatch(legacyNotification("tapBehavior" to """{ "__typename": "NoSuchTapBehavior" }"""))
    }

    @Test
    fun anUnparseableDeliveredAtIsSkipped() {
        assertSkippedWithoutDispatch(legacyNotification("deliveredAt" to "\"yesterday\""))
    }

    @Test
    fun aMalformedAttachmentUrlIsSkipped() {
        assertSkippedWithoutDispatch(legacyNotification("attachment" to """{ "type": "IMAGE", "url": "not a url" }"""))
    }

    @Test
    fun aNonStringConversionTagIsSkipped() {
        assertSkippedWithoutDispatch(legacyNotification("conversionTags" to """[ "tag", {} ]"""))
    }

    @Test
    fun aConversionTagsValueThatIsNotAnArrayIsSkipped() {
        assertSkippedWithoutDispatch(legacyNotification("conversionTags" to "\"tag\""))
    }

    @Test
    fun aValidLegacyNotificationStillReachesTheDispatcher() {
        val notificationDispatcher = mock<NotificationDispatcher>()
        val receiver = makeReceiver(notificationDispatcher)

        receiver.onMessageReceivedData(mapOf("rover" to legacyNotification("conversionTags" to """[ "tag" ]""")))

        verify(notificationDispatcher).ingest(any(), anyOrNull())
    }

    @Test
    fun aSkippedPayloadIsLoggedWithoutItsContents() {
        val sentinel = "SENTINEL-4f9c0d"
        val payloads = listOf(
            """{ not json $sentinel""",
            """{"notification": "$sentinel"}""",
            legacyNotification("tapBehavior" to """{ "__typename": "$sentinel" }"""),
            legacyNotification("deliveredAt" to "\"$sentinel\""),
            legacyNotification("attachment" to """{ "type": "IMAGE", "url": "$sentinel" }"""),
            legacyNotification("conversionTags" to """[ { "token": "$sentinel" } ]"""),
        )

        payloads.forEach { payload ->
            capturedLog.clear()
            assertSkippedWithoutDispatch(payload)

            // The receiver's verbose raw-parameters line is the one deliberate exception.
            val leaked = capturedLog.filterNot { it.startsWith("Received a push notification. Raw parameters:") }
                .filter { it.contains(sentinel) }
            assertThat("payload contents leaked into the log for $payload: $leaked", leaked.isEmpty(), equalTo(true))

            val skipLine = Regex("""Malformed .+ received \(\w+Exception\)\. Ignoring\. \(payload length: ${payload.length}\)""")
            assertThat("no scrubbed skip warning for $payload: $capturedLog", capturedLog.any { skipLine.matches(it) }, equalTo(true))
        }
    }
}
