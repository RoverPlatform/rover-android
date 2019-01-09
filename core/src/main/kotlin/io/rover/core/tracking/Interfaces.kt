package io.rover.core.tracking

import io.rover.core.data.domain.Attributes
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
