package io.rover.sdk.example

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import io.rover.example.R
import io.rover.sdk.core.CoreAssembler
import io.rover.sdk.core.Rover
import io.rover.sdk.debug.DebugAssembler
import io.rover.sdk.experiences.ExperiencesAssembler
import io.rover.sdk.location.LocationAssembler
import io.rover.sdk.notifications.NotificationsAssembler
import io.rover.sdk.ticketmaster.TicketmasterAssembler

class ExampleApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        Rover.installSaneGlobalHttpCache(this)

        // Initialize the Rover sdk
        Rover.initialize(
            CoreAssembler(
                accountToken = getString(R.string.rover_api_token),
                application = this,
                urlSchemes = listOf(getString(R.string.rover_uri_scheme)),
                associatedDomains = listOf(getString(R.string.rover_associated_domain))
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