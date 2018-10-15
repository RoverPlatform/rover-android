package io.rover.app.inbox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.view.Menu
import android.view.MenuItem
import io.rover.account.AuthService
import io.rover.account.ui.LoginActivity
import io.rover.core.Rover
import io.rover.core.deviceIdentification
import io.rover.core.permissionsNotifier
import io.rover.notifications.ui.NotificationCenterListView
import io.rover.core.platform.DeviceIdentificationInterface
import kotlinx.android.synthetic.main.activity_main.demoNotificationCentre
import kotlinx.android.synthetic.main.activity_main.deviceIdentifier
import kotlinx.android.synthetic.main.activity_main.toolbar

class MainActivity : AppCompatActivity() {

    private val authService: AuthService by lazy {
        (this.application as InboxApplication).authService
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(toolbar)

        (findViewById(R.id.demoNotificationCentre) as NotificationCenterListView).activity = this

        demoNotificationCentre.activity = this

        // if not logged in:
        if (!authService.isLoggedIn) {
            startActivity(
                Intent(this, LoginActivity::class.java)
            )
            finish()
        }

        deviceIdentifier.text =
            Rover.sharedInstance.deviceIdentification.installationIdentifier

        makePermissionsAttempt()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_inbox, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        if (id == R.id.action_sign_out) {
            authService.logOut()
            startActivity(
                Intent(this, LoginActivity::class.java)
            )
            finish()
        }

        return super.onOptionsItemSelected(item)
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
