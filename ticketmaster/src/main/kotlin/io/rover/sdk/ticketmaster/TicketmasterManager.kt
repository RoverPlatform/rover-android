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

package io.rover.sdk.ticketmaster

import android.os.Bundle
import com.ticketmaster.discoveryapi.models.DiscoveryEvent
import com.ticketmaster.purchase.action.TMCheckoutEndReason
import com.ticketmaster.purchase.action.TMTicketSelectionEndReason
import io.rover.sdk.core.data.graphql.putProp
import io.rover.sdk.core.data.graphql.safeOptString
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.events.domain.Event
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.platform.whenNotNull
import io.rover.sdk.core.privacy.PrivacyService
import org.json.JSONException
import org.json.JSONObject

class TicketmasterManager(
    private val userInfo: UserInfoInterface,
    localStorage: LocalStorage,
    private val privacyService: PrivacyService,
    private val eventQueue: EventQueueServiceInterface
) : TicketmasterAuthorizer, TicketmasterAnalytics, PrivacyService.TrackingEnabledChangedListener {
    private val storage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "ticketmaster"
        private const val TICKETMASTER_MAP_KEY = "ticketmaster"
    }

    override fun setTicketmasterId(id: String) {
        if (privacyService.trackingMode != PrivacyService.TrackingMode.Default) {
            log.i("Ticketmaster ID set while privacy is in anonymous/anonymized mode, ignored")
            return
        }
        member = Member(
            ticketmasterID = id,
            email = null,
            firstName = null
        )
        updateUserInfoWithMemberAttributes()
        log.i("Ticketmaster signed in with '$id'.")
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateUserInfoWithMemberAttributes() {
        val localPropertiesMap = member?.getNonNullPropertiesMap()

        userInfo.update {
            if (it.containsKey(TICKETMASTER_MAP_KEY)) {
                val tmAttributes = it.getValue(TICKETMASTER_MAP_KEY) as MutableMap<String, Any>
                localPropertiesMap?.forEach { (propertyName, propertyValue) ->
                    tmAttributes[propertyName] = propertyValue
                }
                it[TICKETMASTER_MAP_KEY] = tmAttributes
            } else {
                if (localPropertiesMap?.isNotEmpty() == true) it[TICKETMASTER_MAP_KEY] = localPropertiesMap
            }
        }
    }

    override fun clearCredentials() {
        member = null
        userInfo.update { it.remove(TICKETMASTER_MAP_KEY) }
        log.i("Ticketmaster signed out.")
    }

    private var member: Member?
        get() {
            val storageJson = storage["member"]
            return storageJson.whenNotNull { memberString ->
                try {
                    Member.decodeJson(JSONObject(memberString))
                } catch (e: JSONException) {
                    log.w("Invalid JSON in ticketmaster manager storage, ignoring: $e")
                    null
                }
            }
        }
        set(value) {
            storage["member"] = value?.encodeJson().toString()
        }

    data class Member(
        val ticketmasterID: String?,
        val email: String?,
        val firstName: String?
    ) {
        companion object

        fun getNonNullPropertiesMap(): Map<String, String> {
            val propertiesMap = mutableMapOf<String, String>()
            ticketmasterID.whenNotNull { propertiesMap.put(Member::ticketmasterID.name, it)}
            email.whenNotNull { propertiesMap.put(Member::email.name, it) }
            firstName.whenNotNull { propertiesMap.put(Member::firstName.name, it) }
            return propertiesMap
        }
    }

    override fun onTrackingModeChanged(trackingMode: PrivacyService.TrackingMode) {
        if (trackingMode != PrivacyService.TrackingMode.Default) {
            clearCredentials()
        }
    }

    override fun postTicketmasterEvent(action: String, bundle: Bundle?) {
        if (privacyService.trackingMode != PrivacyService.TrackingMode.Default) return

        val screenName = TMScreenActionToRoverNames[action] ?: return

        val attributes = mutableMapOf<String, Any>("screenName" to screenName)

        val eventAttributes = mutableMapOf<String, Any>()
        val venueAttributes = mutableMapOf<String, Any>()

        bundle?.let {
            with(bundle) {
                getString("event_id")?.let { eventAttributes.put("id", it) }
                getString("event_name")?.let { eventAttributes.put("name", it) }
                getString("event_date")?.let { eventAttributes.put("date", it) }
                getString("event_image_url")?.let { eventAttributes.put("imageURL", it) }

                (getString("venue_id") ?: getString("venu_id"))?.let {
                    venueAttributes.put("id", it)
                }

                getString("venue_name")?.let { venueAttributes.put("name", it) }

                getString("current_ticket_count")?.let { attributes.put("currentTicketCount", it) }
            }
        }

        if (eventAttributes.isNotEmpty()) attributes.put("event", eventAttributes)
        if (venueAttributes.isNotEmpty()) attributes.put("venue", venueAttributes)

        val event = Event("Screen Viewed", attributes)
        eventQueue.trackEvent(event, "ticketmaster")
    }

    override fun onTicketSelectionStarted(event: DiscoveryEvent) {
        if (privacyService.trackingMode != PrivacyService.TrackingMode.Default) return
        val roverEvent = event.toRoverEvent("Did Begin Ticket Selection")
        eventQueue.trackEvent(roverEvent, "ticketmaster")
    }

    override fun onTicketSelectionFinished(event: DiscoveryEvent, reason: TMTicketSelectionEndReason) {
        if (privacyService.trackingMode != PrivacyService.TrackingMode.Default) return

        val roverEvent = event.toRoverEvent("Did End Ticket Selection", reason.name)
        eventQueue.trackEvent(roverEvent, "ticketmaster")
    }

    override fun onCheckoutStarted(event: DiscoveryEvent) {
        if (privacyService.trackingMode != PrivacyService.TrackingMode.Default) return

        val roverEvent = event.toRoverEvent("Did Begin Checkout")
        eventQueue.trackEvent(roverEvent, "ticketmaster")
    }

    override fun onCheckoutFinished(event: DiscoveryEvent, reason: TMCheckoutEndReason) {
        if (privacyService.trackingMode != PrivacyService.TrackingMode.Default) return

        val roverEvent = event.toRoverEvent("Did End Checkout", reason.name)
        eventQueue.trackEvent(roverEvent, "ticketmaster")
    }
}

fun TicketmasterManager.Member.Companion.decodeJson(json: JSONObject): TicketmasterManager.Member {
    return TicketmasterManager.Member(
        ticketmasterID = json.safeOptString(TicketmasterManager.Member::ticketmasterID.name)
            ?: json.safeOptString("hostID")
            ?: json.safeOptString("teamID"),
        email = json.safeOptString(TicketmasterManager.Member::email.name),
        firstName = json.safeOptString(TicketmasterManager.Member::firstName.name)
    )
}

fun TicketmasterManager.Member.encodeJson(jsonObject: JSONObject = JSONObject()): JSONObject {
    return jsonObject.apply {
        listOf(
            TicketmasterManager.Member::ticketmasterID,
            TicketmasterManager.Member::email,
            TicketmasterManager.Member::firstName
        ).forEach {
            putProp(this@encodeJson, it)
        }
    }
}

internal val TMScreenActionToRoverNames = mapOf(
    "com.ticketmaster.presencesdk.eventanalytic.action.MYTICKETSCREENSHOWED" to "My Tickets",
    "com.ticketmaster.presencesdk.eventanalytic.action.MANAGETICKETSCREENSHOWED" to "Manage Ticket",
    "com.ticketmaster.presencesdk.eventanalytic.action.ADDPAYMENTINFOSCREENSHOWED" to "Add Payment Info",
    "com.ticketmaster.presencesdk.eventanalytic.action.MYTICKETBARCODESCREENSHOWED" to "Ticket Barcode",
    "com.ticketmaster.presencesdk.eventanalytic.action.TICKETDETAILSSCREENSHOWED" to "Ticket Details",
    "com.ticketmaster.presencesdk.eventanalytic.action.ACTION_ADD_TO_WALLET_INITIATE" to "Add Ticket To Wallet Button Tapped",
    "com.ticketmaster.presencesdk.eventanalytic.action.TRANSFERINITIATED" to "Ticket Transfer Send Button Tapped",
    "com.ticketmaster.presencesdk.eventanalytic.action.TRANSFERCANCELLED" to "Ticket Transfer Cancel Button Tapped"
)

private fun DiscoveryEvent.toRoverEvent(eventName: String, reason: String? = null): Event {
    val attributes = mutableMapOf<String, Any>()

    reason?.let { attributes["reason"] = it }

    val eventAttributes = mutableMapOf<String, Any>()

    discoveryID?.let { eventAttributes["id"] = it }
    name?.let { eventAttributes["name"] = it }
    imageMetadataList?.firstOrNull()?.url?.let { eventAttributes["imageURL"] = it }
    type?.let { eventAttributes["type"] = it }

    if (eventAttributes.isNotEmpty()) {
        attributes["event"] = eventAttributes
    }

    val venueAttributes = mutableMapOf<String, Any>()

    venues?.first()?.name?.let { venueAttributes["name"] = it }
    venues?.first()?.discoveryID?.let { venueAttributes["id"] = it }

    if (venueAttributes.isNotEmpty()) {
        attributes["venue"] = venueAttributes
    }

    return Event(eventName, attributes)
}
