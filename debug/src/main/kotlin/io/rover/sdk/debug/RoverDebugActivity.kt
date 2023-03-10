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
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import io.rover.debug.R
import io.rover.sdk.core.Rover
import io.rover.sdk.core.logging.log

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
            val rover = Rover.failableShared
            if (rover == null) {
                log.e("RoverDebugActivity cannot work if Rover is not initialized.  Ignoring.")
                return
            }
            val debugPreferences = rover.resolve(DebugPreferences::class.java)
            if (debugPreferences == null) {
                log.e("RoverDebugActivity cannot work if Rover is not initialized, but DebugPreferences is not registered in the Rover container. Ensure DebugAssembler() is in Rover.initialize(). Ignoring.")
                return
            }

            preferenceManager.sharedPreferencesName = debugPreferences.sharedPreferencesName
            preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(
                this::sharedPreferenceChangeListener
            )
            preferenceScreen = debugPreferences.constructPreferencesDefinition(this.preferenceManager)
        }
    }

    companion object {
        fun makeIntent(context: Context): Intent = Intent(context, RoverDebugActivity::class.java)
    }
}
