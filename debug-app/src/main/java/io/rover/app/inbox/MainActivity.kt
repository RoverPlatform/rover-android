package io.rover.app.inbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import io.rover.core.Rover
import io.rover.core.deviceIdentification
import io.rover.core.permissionsNotifier
import io.rover.notifications.ui.NotificationCenterListView
import kotlinx.android.synthetic.main.activity_main.demoNotificationCentre
import kotlinx.android.synthetic.main.activity_main.deviceIdentifier
import kotlinx.android.synthetic.main.activity_main.toolbar

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(toolbar)

        findViewById<NotificationCenterListView>(R.id.demoNotificationCentre).activity = this

        demoNotificationCentre.activity = this

        deviceIdentifier.text =
            Rover.sharedInstance.deviceIdentification.installationIdentifier

        makePermissionsAttempt()
    }

    private fun makePermissionsAttempt() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

                AlertDialog.Builder(this)
                    .setMessage("Inbox App would like to use your location to discover Geofences and Beacons.")
                    .setNeutralButton("Got it") { _, _ ->
                        makePermissionsAttempt()
                    }
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    0
                )
            }
        } else {
            // Permission has already been granted
            Rover.sharedInstance.permissionsNotifier.permissionGranted(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val perms = permissions.zip(grantResults.toList()).associate { it }

        if(perms[Manifest.permission.ACCESS_FINE_LOCATION] == PackageManager.PERMISSION_GRANTED) {
            Rover.sharedInstance.permissionsNotifier.permissionGranted(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

}
