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

package io.rover.sdk.adobeExperience

import io.rover.sdk.core.data.graphql.safeOptString
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.platform.whenNotNull
import io.rover.sdk.core.privacy.PrivacyService
import org.json.JSONException
import org.json.JSONObject

class AdobeExperienceManager(
    private val userInfo: UserInfoInterface,
    localStorage: LocalStorage,
    private val privacyService: PrivacyService,
) : AdobeExperienceAuthorizer, PrivacyService.TrackingEnabledChangedListener {
    private val storage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "adobeExperience"
        private const val ADOBE_EXPERIENCE_ECID_KEY = "ecid"
    }

    override fun setECID(ecid: String) {
        if (privacyService.trackingMode != PrivacyService.TrackingMode.Default) {
            log.i("Adobe Experience ECID set while privacy is in anonymous/anonymized mode, ignored")
            return
        }
        adobeECID = ecid
        updateUserInfoWithMemberAttributes()
        log.i("Adobe Experience Platform signed in with '$ecid'.")
    }

    private fun updateUserInfoWithMemberAttributes() {
        userInfo.update { userInfo ->
            adobeECID.whenNotNull { ecid ->
                userInfo[ADOBE_EXPERIENCE_ECID_KEY] = ecid
            }
        }
    }

    override fun clearCredentials() {
        adobeECID = null
        userInfo.update { it.remove(ADOBE_EXPERIENCE_ECID_KEY) }
        log.i("Adobe Experience Platform signed out.")
    }

    private var adobeECID: String?
        get() {
            val storageJson = storage[ADOBE_EXPERIENCE_ECID_KEY]
            return storageJson.whenNotNull { ecidString ->
                try {
                    JSONObject(ecidString).safeOptString(ADOBE_EXPERIENCE_ECID_KEY)
                } catch (e: JSONException) {
                    log.w("Invalid JSON in adobe mobile manager storage, ignoring: $e")
                    null
                }
            }
        }
        set(value) {
            if (value != null) {
                storage[ADOBE_EXPERIENCE_ECID_KEY] = JSONObject()
                        .put(ADOBE_EXPERIENCE_ECID_KEY, value)
                        .toString()
            } else {
                storage[ADOBE_EXPERIENCE_ECID_KEY] = null
            }
        }

    override fun onTrackingModeChanged(trackingMode: PrivacyService.TrackingMode) {
        if (trackingMode != PrivacyService.TrackingMode.Default) {
            clearCredentials()
        }
    }
}
