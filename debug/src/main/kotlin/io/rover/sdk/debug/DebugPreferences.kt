package io.rover.sdk.debug

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import io.rover.debug.R
import io.rover.sdk.core.platform.DeviceIdentificationInterface

class DebugPreferences(
    context: Context,
    private val deviceIdentification: DeviceIdentificationInterface
) {
    val sharedPreferencesName = "io.rover.debug.settings"
    private val sharedPreferences = context.getSharedPreferences(sharedPreferencesName, MODE_PRIVATE)

    /**
     * Constructs a [Preferences] object that describes the Debug screen preferences.
     */
    fun constructPreferencesDefinition(preferenceManager: PreferenceManager): PreferenceScreen {
        return preferenceManager.createPreferenceScreen(preferenceManager.context).apply {
            title = context.getText(R.string.debug_settings_title)
            addPreference(
                SwitchPreferenceCompat(
                    preferenceManager.context
                ).apply {
                    title = context.getText(R.string.debug_settings_test_device)
                    key = testDevicePreferencesKey
                }
            )
            addPreference(
                EditTextPreference(
                    preferenceManager.context
                ).apply {
                    isSelectable = true
                    isPersistent = false
                    title = context.getText(R.string.debug_settings_device_name)
                    summary = deviceIdentification.deviceName
                    key = "edit_device_name"
                    dialogMessage = "Set the device name to be used for testing."
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                        deviceIdentification.deviceName = newValue as String
                        summary = newValue
                        true
                    }
                }
            )
            addPreference(
                EditTextPreference(
                    preferenceManager.context
                ).apply {
                    isSelectable = false
                    isPersistent = false
                    title = context.getText(R.string.debug_settings_device_id)
                    summary = deviceIdentification.installationIdentifier
                }
            )
        }
    }

    fun currentTestDeviceState(): Boolean {
        return sharedPreferences.getBoolean(testDevicePreferencesKey, false)
    }

    private val testDevicePreferencesKey = "test-device"
}
