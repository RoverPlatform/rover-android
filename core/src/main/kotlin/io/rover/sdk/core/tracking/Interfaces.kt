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

package io.rover.sdk.core.tracking

import io.rover.sdk.core.data.domain.Attributes
import java.util.UUID

interface SessionStoreInterface {
    /**
     * Enters a session.
     *
     * Returns the new session's UUID, or, if a session is already active, null.
     */
    fun enterSession(sessionKey: Any, sessionEventName: String, attributes: Attributes)

    fun leaveSession(sessionKey: Any)

    /**
     * Returns the soonest time that a session is going to expire.
     */
    fun soonestExpiryInSeconds(keepAliveSeconds: Int): Int?

    /**
     * Returns the sessions that are expired and should have their event emitted.
     *
     * Such sessions will only be returned once; they are deleted.
     */
    fun collectExpiredSessions(keepAliveSeconds: Int): List<ExpiredSession>

    data class ExpiredSession(
        val sessionKey: Any,
        val uuid: UUID,
        val eventName: String,
        val attributes: Attributes,
        val durationSeconds: Int
    )
}

interface SessionTrackerInterface {
    /**
     * Indicate a session is opening for the given session key.  The Session Key should be a value
     * object (a Kotlin data class, a string, etc.) that properly implements hashcode, equals, and
     * an exhaustive version of toString(). This value object should describe the given semantic
     * item the user is looking at (a given experience, a given view, etc.).
     *
     * Note that the values need to be unique amongst the different event sources in the app, so be
     * particularly careful with string values or data class class names.
     *
     * A [sessionEventName] must be provided so that the Session Tracker can emit session viewed
     * after the timeout completes.
     */
    fun enterSession(
        sessionKey: Any,
        sessionStartEventName: String,
        sessionEventName: String,
        attributes: Attributes
    )

    /**
     * Indicate a session is being left.  See [enterSession] for an explanation of session key.
     */
    fun leaveSession(
        sessionKey: Any,
        sessionEndEventName: String,
        attributes: Attributes
    )
}
