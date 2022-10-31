package io.rover.example

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import io.rover.core.CoreAssembler
import io.rover.core.RoverCampaigns
import io.rover.debug.DebugAssembler
import io.rover.experiences.ExperiencesAssembler
import io.rover.location.LocationAssembler
import io.rover.notifications.NotificationsAssembler
import io.rover.ticketmaster.TicketmasterAssembler

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