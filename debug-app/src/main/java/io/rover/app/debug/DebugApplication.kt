package io.rover.app.debug

import android.app.Application
import android.content.Intent
import com.google.firebase.iid.FirebaseInstanceId
import io.rover.core.CoreAssembler
import io.rover.core.Rover
import io.rover.debug.DebugAssembler
import io.rover.experiences.ExperiencesAssembler
import io.rover.location.LocationAssembler
import io.rover.notifications.NotificationsAssembler
import io.rover.ticketmaster.TicketmasterAssembler
import timber.log.Timber


class DebugApplication : Application() {
    private val roverBaseUrl by lazy { resources.getString(R.string.rover_endpoint) }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Rover.installSaneGlobalHttpCache(this)

        Rover.initialize(
            CoreAssembler(
                accountToken = getString(R.string.rover_api_token),
                application = this,
                urlSchemes = listOf("rv-sean-rucker"),
                endpoint = "$roverBaseUrl/graphql"
            ),
            NotificationsAssembler(
                applicationContext = this,
                smallIconResId = R.mipmap.rover_notification_icon,
                notificationCenterIntent = Intent(applicationContext, DebugMainActivity::class.java)
            ) { tokenCallback ->
                FirebaseInstanceId.getInstance().instanceId.addOnCompleteListener { task ->
                    tokenCallback(task.result?.token)
                }
            },
            ExperiencesAssembler(),
            LocationAssembler(),
            DebugAssembler(),
            TicketmasterAssembler()
        )
    }
}
