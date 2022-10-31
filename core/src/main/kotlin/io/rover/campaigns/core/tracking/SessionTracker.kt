package io.rover.campaigns.core.tracking

import android.os.Handler
import android.os.Looper
import io.rover.campaigns.core.data.domain.Attributes
import io.rover.campaigns.core.data.graphql.operations.data.encodeJson
import io.rover.campaigns.core.data.graphql.operations.data.toAttributesHash
import io.rover.campaigns.core.data.graphql.safeGetString
import io.rover.campaigns.core.data.graphql.safeOptInt
import io.rover.campaigns.core.events.EventQueueService.Companion.ROVER_NAMESPACE
import io.rover.campaigns.core.events.EventQueueServiceInterface
import io.rover.campaigns.core.events.domain.Event
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.platform.LocalStorage
import io.rover.campaigns.core.platform.whenNotNull
import org.json.JSONObject
import java.util.Date
import java.util.UUID
import kotlin.math.max

class SessionTracker(
    private val eventQueueService: EventQueueServiceInterface,

    private val sessionStore: SessionStoreInterface,

    /**
     * The number of seconds to leave the session open for in the event that the user leaves
     * temporarily.
     */
    private val keepAliveTime: Int
) : SessionTrackerInterface {
    private val timerHandler = Handler(Looper.getMainLooper())

    override fun enterSession(
        sessionKey: Any,
        sessionStartEventName: String,
        sessionEventName: String,
        attributes: Attributes
    ) {
        log.v("Entering session $sessionKey")
        sessionStore.enterSession(sessionKey, sessionEventName, attributes)

        eventQueueService.trackEvent(
            Event(
                sessionStartEventName,
                attributes
            ),
            ROVER_NAMESPACE
        )
    }

    private fun updateTimer() {
        timerHandler.removeCallbacksAndMessages(this::timerCallback)
        sessionStore.soonestExpiryInSeconds(keepAliveTime).whenNotNull { soonestExpiry ->
            if (soonestExpiry == 0) {
                // execute the timer callback directly.
                timerCallback()
            } else {
                timerHandler.postDelayed(
                    this::timerCallback, soonestExpiry * 1000L
                )
            }
        }
    }

    private fun timerCallback() {
        log.v("Emitting events for expired sessions.")
        sessionStore.collectExpiredSessions(keepAliveTime).forEach { expiredSession ->
            log.v("Session closed: ${expiredSession.sessionKey}")
            eventQueueService.trackEvent(
                Event(
                    expiredSession.eventName,
                    hashMapOf(
                        Pair("duration", expiredSession.durationSeconds)
                    ) + expiredSession.attributes
                ),
                ROVER_NAMESPACE
            )
        }

        updateTimer()
    }

    override fun leaveSession(
        sessionKey: Any,
        sessionEndEventName: String,
        attributes: Attributes
    ) {
        log.v("Leaving session $sessionKey")
        sessionStore.leaveSession(sessionKey)

        eventQueueService.trackEvent(
            Event(
                sessionEndEventName,
                attributes
            ),
            ROVER_NAMESPACE
        )

        updateTimer()
    }
}

class SessionStore(
    localStorage: LocalStorage
) : SessionStoreInterface {
    private val store = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    init {
        gc()
    }

    override fun enterSession(sessionKey: Any, sessionEventName: String, attributes: Attributes) {
        val session = getEntry(sessionKey)?.copy(
            // clear closedAt to avoid expiring the session if it is being re-opened.
            closedAt = null
        ) ?: SessionEntry(
            UUID.randomUUID(),
            sessionEventName,
            Date(),
            attributes,
            null
        )

        setEntry(sessionKey, session)
    }

    override fun leaveSession(sessionKey: Any) {
        val existingEntry = getEntry(sessionKey)

        if (existingEntry != null) {
            // there is indeed a session open for the given key, mark it as expiring.
            setEntry(
                sessionKey, existingEntry.copy(
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
                log.w("Invalid JSON appeared in Session Store, ignoring: ${exception.message}")
                null
            }
        }
    }

    private fun setEntry(sessionKey: Any, sessionEntry: SessionEntry) {
        store[sessionKey.toString()] = sessionEntry.encodeJson().toString()
    }

    override fun soonestExpiryInSeconds(keepAliveSeconds: Int): Int? {
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

    override fun collectExpiredSessions(keepAliveSeconds: Int): List<SessionStoreInterface.ExpiredSession> {
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
            SessionStoreInterface.ExpiredSession(
                key,
                entry.uuid,
                entry.sessionEventName,
                entry.sessionAttributes,
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

    data class SessionEntry(
        val uuid: UUID,

        val sessionEventName: String,

        /**
         * When was this session started?
         */
        val startedAt: Date,

        /**
         * Any other attributes to include with the session event.
         */
        val sessionAttributes: Attributes,

        /**
         * When was the session closed?
         */
        val closedAt: Date?
    ) {
        fun encodeJson(): JSONObject {
            return JSONObject().apply {
                put("uuid", uuid.toString())
                put("session-event-name", sessionEventName)
                put("started-at", startedAt.time / 1000)
                put("session-attributes", sessionAttributes.encodeJson())
                if (closedAt != null) {
                    put("closed-at", closedAt.time / 1000)
                }
            }
        }

        companion object {
            fun decodeJson(jsonObject: JSONObject): SessionEntry {
                return SessionEntry(
                    UUID.fromString(jsonObject.safeGetString("uuid")),
                    jsonObject.safeGetString("session-event-name"),
                    Date(jsonObject.getInt("started-at") * 1000L),
                    jsonObject.getJSONObject("session-attributes").toAttributesHash(),
                    jsonObject.safeOptInt("closed-at").whenNotNull { Date(it * 1000L) }
                )
            }
        }
    }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "session-store"
        private const val CLEANUP_TIME = 3600 * 1000L // 1 hour.
    }
}