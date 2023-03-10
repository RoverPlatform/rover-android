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

package io.rover.sdk.core.version

import android.content.Context
import io.rover.sdk.core.events.EventQueueService.Companion.ROVER_NAMESPACE
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.events.domain.Event
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage

class VersionTracker(
    private val applicationContext: Context,
    private val eventQueueService: EventQueueServiceInterface,
    localStorage: LocalStorage
) : VersionTrackerInterface {
    override fun trackAppVersion() {
        // we want to get the version name/code of the app the SDK is installed in, not the SDK
        // itself, so this means we cannot use BuildConfig.  Instead, we will introspect it from the
        // package manifest/metadata.
        val packageInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)

        val versionCode = packageInfo.versionCode
        val versionName = packageInfo.versionName

        val lastSeenCode = store[LAST_SEEN_VERSION_CODE_KEY]?.toIntOrNull()
        val lastSeenName = store[LAST_SEEN_VERSION_NAME_KEY] ?: ""

        // emit event:
        when (lastSeenCode) {
            // no change:
            versionCode -> Unit
            // fresh install, or at least first update to include the Rover SDK:
            null -> trackAppInstalled(versionCode, versionName)
            // downgrade:
            in 0 until lastSeenCode -> trackAppVersionChange(false, lastSeenCode, lastSeenName, versionCode, versionName)
            // upgrade:
            else -> trackAppVersionChange(true, lastSeenCode, lastSeenName, versionCode, versionName)
        }

        // now update our stored values:
        store[LAST_SEEN_VERSION_CODE_KEY] = versionCode.toString()
        store[LAST_SEEN_VERSION_NAME_KEY] = versionName
    }

    private fun trackAppInstalled(versionCode: Int, versionName: String) {
        log.v("App has been installed. Version: $versionName ($versionCode).")
        eventQueueService.trackEvent(
            Event(
                "App Installed",
                hashMapOf()
            ),
            ROVER_NAMESPACE
        )
    }

    private fun trackAppVersionChange(upgrade: Boolean, previousVersionCode: Int, previousVersionName: String, versionCode: Int, versionName: String) {
        val verb = if (upgrade) "updated" else "downgraded"
        log.v("App has been $verb from $previousVersionName ($previousVersionCode) to $versionName ($versionCode).")

        if (upgrade) {
            eventQueueService.trackEvent(
                Event(
                    "App Updated",
                    // the current app version is included as part of Context already thanks to ApplicationContextProvider.
                    hashMapOf(
                        Pair("previousVersion", previousVersionName),
                        Pair("previousBuild", previousVersionCode)
                    )
                ),
                ROVER_NAMESPACE
            )
        }
    }

    private val store = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "version-tracker"
        private const val LAST_SEEN_VERSION_CODE_KEY = "last-seen-version-code"
        private const val LAST_SEEN_VERSION_NAME_KEY = "last-seen-version-name"
    }
}
