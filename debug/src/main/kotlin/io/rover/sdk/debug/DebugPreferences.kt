/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

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
                    dialogTitle = context.getText(R.string.debug_settings_set_device_name)
                    dialogMessage = context.getText(R.string.debug_settings_device_name_description)
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
