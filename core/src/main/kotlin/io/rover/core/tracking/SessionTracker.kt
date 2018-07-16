package io.rover.core.tracking

import android.os.Handler
import android.os.Looper
import io.rover.core.data.domain.AttributeValue
import io.rover.core.data.domain.Attributes
import io.rover.core.data.graphql.operations.data.encodeJson
import io.rover.core.data.graphql.operations.data.toFlatAttributesHash
import io.rover.core.data.graphql.safeGetString
import io.rover.core.data.graphql.safeOptInt
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.events.domain.Event
import io.rover.core.logging.log
import io.rover.core.platform.LocalStorage
import io.rover.core.platform.whenNotNull
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
): SessionTrackerInterface {
    private val timerHandler = Handler(Looper.getMainLooper())

    override fun enterSession(
        sessionKey: Any,
        sessionStartEventName: String,
        sessionEventName: String,
        attributes: Attributes
    ) {
        sessionStore.enterSession(sessionKey, sessionEventName, attributes)

        eventQueueService.trackEvent(
            Event(
                sessionStartEventName,
                attributes
            )
        )
    }

    private fun updateTimer() {
        timerHandler.removeCallbacksAndMessages(this::timerCallback)
        sessionStore.soonestExpiryInSeconds(keepAliveTime).whenNotNull { soonestExpiry ->
            if(soonestExpiry == 0) {
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
        log.v("Emitting events for expired sessions. ${hashCode()}")
        sessionStore.collectExpiredSessions(keepAliveTime).forEach { expiredSession ->
            eventQueueService.trackEvent(
                Event(
                    expiredSession.eventName,
                    hashMapOf(
                        Pair("duration", AttributeValue.Integer(expiredSession.durationSeconds))
                    )
                )
            )
        }

        updateTimer()
    }

    override fun leaveSession(sessionKey: Any,
        sessionEndEventName: String,
        attributes: Attributes
    ) {
        sessionStore.leaveSession(sessionKey)

        eventQueueService.trackEvent(
            Event(
                sessionEndEventName,
                attributes
            )
        )

        updateTimer()
    }
}

class SessionStore(
    localStorage: LocalStorage
): SessionStoreInterface {
    private val store = localStorage.getKeyValueStorageFor(STORAGE_IDENTIFIER)

    override fun enterSession(sessionKey: Any, sessionEventName: String, attributes: Attributes) {
        val session = getEntry(sessionKey) ?: SessionEntry(
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

        if(existingEntry != null) {
            // there is indeed a session open for the given key, mark it as expiring.
            setEntry(sessionKey, existingEntry.copy(
                closedAt = Date()
            ))
        }

        gc()
    }

    private fun getEntry(sessionKey: Any): SessionEntry? {
        val entryJson = store[sessionKey.toString()].whenNotNull { JSONObject(it) }

        return entryJson.whenNotNull { try {
            SessionEntry.decodeJson(it)
        } catch (exception: Exception) {
            log.w("Invalid JSON appeared in Session Store, ignoring: ${exception.message}")
            null
        } }
    }

    private fun setEntry(sessionKey: Any, sessionEntry: SessionEntry) {
        store[sessionKey.toString()] = sessionEntry.encodeJson().toString()
    }


    override fun soonestExpiryInSeconds(keepAliveSeconds: Int): Int? {
        // gather stale expiring session entries that have passed.
        val earliestExpiry = store.keys
            .mapNotNull { key -> getEntry(key) }
            .mapNotNull { entry -> entry.closedAt?.time  }
            .map { closedAt -> closedAt + (keepAliveSeconds * 1000L) }
            // differential from current time in seconds, assuming expiry in the future.
            .map { expiryTimeMsEpoch ->
                ((expiryTimeMsEpoch - Date().time) / 1000).toInt()
            }
            .min()

        // if there's a negative number, return 0 because there's already expired entries that need
        // to be dealt with now.

       return earliestExpiry.whenNotNull {  earliest ->
           max(earliest, 0) }
    }

    override fun collectExpiredSessions(keepAliveSeconds: Int): List<SessionStoreInterface.ExpiredSession> {
        val expiringEntries =  store.keys
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
            log.v("Removing Expired entry $key")
            store.unset(key)
        }

        return expiringEntries.map { (_, entry) ->
            SessionStoreInterface.ExpiredSession(
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

            if(entry == null) {
                log.e("GC: '$key' was missing or invalid.  Deleting it.")
                store[key] = null
                return@forEach
            }

            if(entry.startedAt.before(Date(Date().time - CLEANUP_TIME))) {
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
                if(closedAt != null) {
                    put("closed-at", closedAt.time / 1000)
                }
            }
        }

        companion object {
            fun decodeJson(jsonObject: JSONObject): SessionEntry? {
                return SessionEntry(
                    UUID.fromString(jsonObject.safeGetString("uuid")),
                    jsonObject.safeGetString("session-event-name"),
                    Date(jsonObject.getInt("started-at") * 1000L),
                    jsonObject.getJSONObject("session-attributes").toFlatAttributesHash(),
                    jsonObject.safeOptInt("closed-at").whenNotNull { Date(it * 1000L) }
                )
            }
        }
    }

    companion object {
        const val STORAGE_IDENTIFIER = "io.rover.core.tracking.session-store"
        const val CLEANUP_TIME = 3600 * 1000L // 1 hour.
    }
}