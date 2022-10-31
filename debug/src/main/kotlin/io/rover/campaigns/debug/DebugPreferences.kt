package io.rover.campaigns.debug

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import io.rover.campaigns.core.platform.DeviceIdentificationInterface

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
                    isSelectable = false
                    isPersistent = false
                    title = context.getText(R.string.debug_settings_device_name)
                    summary = deviceIdentification.deviceName
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
