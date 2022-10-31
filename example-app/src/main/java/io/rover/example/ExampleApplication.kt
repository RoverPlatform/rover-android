package io.rover.example

import android.app.Application
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import io.rover.campaigns.core.CoreAssembler
import io.rover.campaigns.core.RoverCampaigns
import io.rover.campaigns.debug.DebugAssembler
import io.rover.campaigns.experiences.ExperiencesAssembler
import io.rover.campaigns.location.LocationAssembler
import io.rover.campaigns.notifications.NotificationsAssembler
import io.rover.campaigns.ticketmaster.TicketmasterAssembler

class ExampleApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        RoverCampaigns.installSaneGlobalHttpCache(this)

        // Initialize the Campaigns sdk
        RoverCampaigns.initialize(
            CoreAssembler(
                accountToken = getString(R.string.rover_api_token),
                application = this,
                urlSchemes = listOf(getString(R.string.rover_campaigns_uri_scheme)),
                associatedDomains = listOf(getString(R.string.rover_campaigns_associated_domain))
            ),
            NotificationsAssembler(
                applicationContext = this,
                smallIconResId = R.mipmap.rover_notification_icon
            ) { tokenCallback ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w("RoverExampleApplication", "Fetching FCM registration token failed", task.exception)
                        return@addOnCompleteListener
                    }

                    // Get new FCM registration token
                    val token = task.result
                    tokenCallback(token)
                }
            },
            LocationAssembler(),
            DebugAssembler(),
            TicketmasterAssembler(),
            ExperiencesAssembler()
        )
    }
}