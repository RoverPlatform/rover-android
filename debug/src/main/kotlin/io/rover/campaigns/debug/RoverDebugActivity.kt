package io.rover.campaigns.debug

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import io.rover.campaigns.core.RoverCampaigns
import io.rover.campaigns.core.logging.log

/**
 * This activity displays a list of hidden debug settings for the Rover SDK.
 */
class RoverDebugActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.debug_settings_title)

        supportFragmentManager.beginTransaction()
            .replace(
                android.R.id.content,
                RoverDebugPreferenceFragment()
            )
            .commit()
    }

    class RoverDebugPreferenceFragment : PreferenceFragmentCompat() {

        override fun onDestroy() {
            super.onDestroy()
            preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(
                this::sharedPreferenceChangeListener
            )
        }

        @Suppress("UNUSED_PARAMETER")
        private fun sharedPreferenceChangeListener(sharedPreferences: SharedPreferences, key: String) {
            // unused parameter suppressed because this method needs to match a signature.
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val rover = RoverCampaigns.shared
            if (rover == null) {
                log.e("RoverDebugActivity cannot work if Rover Campaigns is not initialized.  Ignoring.")
                return
            }
            val debugPreferences = rover.resolve(DebugPreferences::class.java)
            if (debugPreferences == null) {
                log.e("RoverDebugActivity cannot work if Rover Campaigns is not initialized, but DebugPreferences is not registered in the Rover Campaigns container. Ensure DebugAssembler() is in RoverCampaigns.initialize(). Ignoring.")
                return
            }

            preferenceManager.sharedPreferencesName = debugPreferences.sharedPreferencesName
            preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(
                this::sharedPreferenceChangeListener
            )
            preferenceScreen = debugPreferences.constructPreferencesDefinition(this.preferenceManager)
        }
    }
}
