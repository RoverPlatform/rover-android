package io.rover.debug

import android.content.SharedPreferences
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.preference.PreferenceFragmentCompat
import io.rover.core.Rover

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
        private val debugPreferences = Rover.sharedInstance.debugPreferences

        override fun onDestroy() {
            super.onDestroy()
            preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(
                this::sharedPreferenceChangeListener
            )
        }

        @Suppress("UNUSED_PARAMETER")
        private fun sharedPreferenceChangeListener(sharedPreferences: SharedPreferences, key: String) {
            // unused parameter suppressed because this method needs to match a signature.
            debugPreferences.notifyChange(key)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = debugPreferences.sharedPreferencesName
            preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(
                this::sharedPreferenceChangeListener
            )
            preferenceScreen = debugPreferences.constructPreferencesDefinition(this.preferenceManager)
        }
    }
}
