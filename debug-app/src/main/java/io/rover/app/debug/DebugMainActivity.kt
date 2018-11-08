package io.rover.app.debug

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import io.rover.core.Rover
import io.rover.core.permissions.PermissionsNotifierInterface
import io.rover.core.permissionsNotifier
import io.rover.experiences.ui.containers.ExperienceActivity
import kotlinx.android.synthetic.main.activity_debug_main.navigation
import kotlinx.android.synthetic.main.activity_debug_main.notification_center
import kotlinx.android.synthetic.main.activity_debug_main.settings_fragment

class DebugMainActivity : AppCompatActivity() {

    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        selectTab(item.itemId)
        return@OnNavigationItemSelectedListener true
    }

    private fun selectTab(itemId: Int) {
        if(itemId == R.id.navigation_settings) {
            // show
            supportFragmentManager.beginTransaction().show(this.settings_fragment).commit()
        } else {
            // hide
            supportFragmentManager.beginTransaction().hide(this.settings_fragment).commit()
        }
        this.notification_center.visibility = if(itemId == R.id.navigation_notifications) View.VISIBLE else View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_debug_main)

        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)

        notification_center.activity = this

        selectTab(R.id.navigation_notifications)

        makePermissionsAttempt()

//         startActivity(
//             ExperienceActivity.makeIntent(this, experienceId = "59e8b9d0d4459d00102c2958", campaignId = null)
//         )
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
                    .setMessage("Debug App would like to use your location to discover Geofences and Beacons.")
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
            Rover.shared?.resolveSingletonOrFail(PermissionsNotifierInterface::class.java)?.permissionGranted(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val perms = permissions.zip(grantResults.toList()).associate { it }

        if(perms[Manifest.permission.ACCESS_FINE_LOCATION] == PackageManager.PERMISSION_GRANTED) {
            Rover.shared?.resolveSingletonOrFail(PermissionsNotifierInterface::class.java)?.permissionGranted(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
}
