package io.rover.sdk.services

import android.os.Handler
import android.os.Looper
import io.rover.sdk.data.events.RoverEvent
import io.rover.sdk.data.graphql.safeGetString
import io.rover.sdk.data.graphql.safeOptInt
import io.rover.sdk.logging.log
import io.rover.sdk.platform.LocalStorage
import io.rover.sdk.platform.debugExplanation
import io.rover.sdk.platform.whenNotNull
import org.json.JSONObject
import java.util.Date
import java.util.UUID
import kotlin.math.max

internal class SessionTracker(
    private val eventEmitter: EventEmitter,

    private val sessionStore: SessionStore,

    /**
     * The number of seconds to leave the session open for in the event that the user leaves
     * temporarily.
     */
    private val keepAliveTime: Int
) {
    private val timerHandler = Handler(Looper.getMainLooper())

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
        sessionStartEvent: RoverEvent,
        sessionEvent: RoverEvent
    ) {
        log.v("Entering session $sessionKey")
        sessionStore.enterSession(sessionKey, sessionEvent)

        eventEmitter.trackEvent(
            sessionStartEvent
        )
    }

    /**
     * Indicate a session is being left.  See [enterSession] for an explanation of session key.
     */
    private fun updateTimer() {
        timerHandler.removeCallbacksAndMessages(this::timerCallback)
        sessionStore.soonestExpiryInSeconds(keepAliveTime).whenNotNull { soonestExpiry ->
            if (soonestExpiry == 0) {
                // execute the timer callback directly.
                timerCallback()
            } else {
                timerHandler.postDelayed(
                    this::timerCallback,
                    soonestExpiry * 1000L
                )
            }
        }
    }

    private fun timerCallback() {
        log.v("Emitting events for expired sessions.")
        sessionStore.collectExpiredSessions(keepAliveTime).forEach { expiredSession ->
            log.v("Session closed: ${expiredSession.sessionKey}")

            val eventToSend: RoverEvent = when (val event = expiredSession.sessionEvent) {
                is RoverEvent.ScreenViewed -> {
                    event.copy(duration = expiredSession.durationSeconds)
                }
                is RoverEvent.ExperienceViewed -> {
                    event.copy(duration = expiredSession.durationSeconds)
                }
                else -> event
            }

            eventEmitter.trackEvent(eventToSend)
        }

        updateTimer()
    }

    fun leaveSession(
        sessionKey: Any,
        sessionEndEvent: RoverEvent
    ) {
        log.v("Leaving session $sessionKey")
        sessionStore.leaveSession(sessionKey)

        eventEmitter.trackEvent(sessionEndEvent)

        updateTimer()
    }
}

internal class SessionStore(
    localStorage: LocalStorage
) {
    private val store = localStorage.getKeyValueStorageFor(STORAGE_IDENTIFIER)

    init {
        gc()
    }

    /**
     * Enters a session.
     *
     * Returns the new session's UUID, or, if a session is already active, null.
     */
    fun enterSession(sessionKey: Any, sessionEvent: RoverEvent) {
        val session = getEntry(sessionKey)?.copy(
            // clear closedAt to avoid expiring the session if it is being re-opened.
            closedAt = null
        ) ?: SessionEntry(
            UUID.randomUUID(),
            Date(),
            sessionEvent,
            null
        )

        setEntry(sessionKey, session)
    }

    fun leaveSession(sessionKey: Any) {
        val existingEntry = getEntry(sessionKey)

        if (existingEntry != null) {
            // there is indeed a session open for the given key, mark it as expiring.
            setEntry(
                sessionKey,
                existingEntry.copy(
                    closedAt = Date()
                )
            )
        }
    }

    private fun getEntry(sessionKey: Any): SessionEntry? {
        val entryJson = store[sessionKey.toString()].whenNotNull { JSONObject(it) }

        return entryJson.whenNotNull {
            try {
                SessionEntry.decodeJson(it)
            } catch (exception: Exception) {
                log.w("Invalid JSON appeared in Session Store, ignoring: ${exception.debugExplanation()}")
                null
            }
        }
    }

    private fun setEntry(sessionKey: Any, sessionEntry: SessionEntry) {
        store[sessionKey.toString()] = sessionEntry.encodeJson().toString()
    }

    /**
     * Returns the soonest time that a session is going to expire.
     */
    fun soonestExpiryInSeconds(keepAliveSeconds: Int): Int? {
        // gather stale expiring session entries that have passed.
        val earliestExpiry = store.keys
            .mapNotNull { key -> getEntry(key) }
            .mapNotNull { entry -> entry.closedAt?.time }
            .map { closedAt -> closedAt + (keepAliveSeconds * 1000L) }
            // differential from current time in seconds, assuming expiry in the future.
            .map { expiryTimeMsEpoch ->
                ((expiryTimeMsEpoch - Date().time) / 1000).toInt()
            }
            .minOrNull()

        // if there's a negative number, return 0 because there's already expired entries that need
        // to be dealt with now.

        return earliestExpiry.whenNotNull { earliest ->
            max(earliest, 0)
        }
    }

    /**
     * Returns the sessions that are expired and should have their event emitted.
     *
     * Such sessions will only be returned once; they are deleted.
     */
    fun collectExpiredSessions(keepAliveSeconds: Int): List<ExpiredSession> {
        val expiringEntries = store.keys
            .mapNotNull { key ->
                getEntry(key).whenNotNull { Pair(key, it) }
            }
            .filter { it.second.closedAt != null }
            .filter { entry ->
                entry.second.closedAt!!.before(
                    Date(
                        Date().time + keepAliveSeconds * 1000L
                    )
                )
            }

        expiringEntries.map { it.first }.forEach { key ->
            log.v("Removing now expired session entry $key from store.")
            store.unset(key)
        }

        return expiringEntries.map { (key, entry) ->
            ExpiredSession(
                key,
                entry.uuid,
                entry.sessionEvent,
                ((entry.closedAt?.time!! - entry.startedAt.time) / 1000L).toInt()
            )
        }
    }

    private fun gc() {
        log.v("Garbage collecting any expired sessions.")

        store.keys.forEach { key ->
            val entry = getEntry(key)

            if (entry == null) {
                log.e("GC: '$key' was missing or invalid.  Deleting it.")
                store[key] = null
                return@forEach
            }

            if (entry.startedAt.before(Date(Date().time - CLEANUP_TIME))) {
                log.w("Cleaning up stale session store key: $key/$entry")
            }
        }
    }

    data class ExpiredSession(
        val sessionKey: Any,
        val uuid: UUID,
        val sessionEvent: RoverEvent,
        val durationSeconds: Int
    )

    data class SessionEntry(
        val uuid: UUID,

        /**
         * When was this session started?
         */
        val startedAt: Date,

        /**
         * Any other attributes to include with the session event.
         */
        val sessionEvent: RoverEvent,

        /**
         * When was the session closed?
         */
        val closedAt: Date?
    ) {
        fun encodeJson(): JSONObject {
            return JSONObject().apply {
                put("uuid", uuid.toString())
                put("started-at", startedAt.time / 1000)
                put("session-attributes", sessionEvent.encodeJson())
                if (closedAt != null) {
                    put("closed-at", closedAt.time / 1000)
                }
            }
        }

        companion object {
            fun decodeJson(jsonObject: JSONObject): SessionEntry {
                return SessionEntry(
                    UUID.fromString(jsonObject.safeGetString("uuid")),
                    Date(jsonObject.getInt("started-at") * 1000L),
                    RoverEvent.decodeJson(jsonObject.getJSONObject("session-attributes")),
                    jsonObject.safeOptInt("closed-at").whenNotNull { Date(it * 1000L) }
                )
            }
        }
    }

    companion object {
        const val STORAGE_IDENTIFIER = "session-store"
        const val CLEANUP_TIME = 3600 * 1000L // 1 hour.
    }
}
