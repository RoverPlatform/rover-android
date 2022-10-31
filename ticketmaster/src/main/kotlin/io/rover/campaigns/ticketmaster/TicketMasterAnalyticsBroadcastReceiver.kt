package io.rover.campaigns.ticketmaster

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.rover.campaigns.core.RoverCampaigns
import io.rover.campaigns.core.events.EventQueueServiceInterface
import io.rover.campaigns.core.events.domain.Event

class TicketMasterAnalyticsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.action?.let { action ->
            TMScreenActionToRoverNames[action]?.let { screenName ->
                trackTicketMasterScreenViewedEvent(intent, screenName)
            }
        }
    }

    private fun trackTicketMasterScreenViewedEvent(intent: Intent, screenName: String) {
        val eventQueue = RoverCampaigns.shared?.resolve(EventQueueServiceInterface::class.java)

        val attributes = mutableMapOf<String, Any>("screenName" to screenName)

        val eventAttributes = mutableMapOf<String, Any>()
        val venueAttributes = mutableMapOf<String, Any>()
        val artistAttributes = mutableMapOf<String, Any>()

        with(intent) {
            getStringExtra("event_id")?.let { eventAttributes.put("id", it) }
            getStringExtra("event_name")?.let { eventAttributes.put("name", it) }
            getStringExtra("event_date")?.let { eventAttributes.put("date", it) }
            getStringExtra("event_image_url")?.let { eventAttributes.put("imageURL", it) }


            (getStringExtra("venue_id") ?: getStringExtra("venu_id"))?.let {
                venueAttributes.put("id", it)
            }

            getStringExtra("venue_name")?.let { venueAttributes.put("name", it) }

            getStringExtra("current_ticket_count")?.let { attributes.put("currentTicketCount", it) }

            getStringExtra("artist_id")?.let { artistAttributes.put("id", it) }
            getStringExtra("artist_name")?.let { artistAttributes.put("name", it) }
        }

        if (eventAttributes.isNotEmpty()) attributes.put("event", eventAttributes)
        if (venueAttributes.isNotEmpty()) attributes.put("venue", venueAttributes)
        if (artistAttributes.isNotEmpty()) attributes.put("artist", artistAttributes)

        val event = Event("Screen Viewed", attributes)
        eventQueue?.trackEvent(event, "ticketmaster")
    }
}

internal val TMScreenActionToRoverNames = mapOf(
    "com.ticketmaster.presencesdk.eventanalytic.action.MYTICKETSCREENSHOWED" to "My Tickets",
    "com.ticketmaster.presencesdk.eventanalytic.action.MANAGETICKETSCREENSHOWED" to "Manage Ticket",
    "com.ticketmaster.presencesdk.eventanalytic.action.ADDPAYMENTINFOSCREENSHOWED" to "Add Payment Info",
    "com.ticketmaster.presencesdk.eventanalytic.action.MYTICKETBARCODESCREENSHOWED" to "Ticket Barcode",
    "com.ticketmaster.presencesdk.eventanalytic.action.TICKETDETAILSSCREENSHOWED" to "Ticket Details"
)
