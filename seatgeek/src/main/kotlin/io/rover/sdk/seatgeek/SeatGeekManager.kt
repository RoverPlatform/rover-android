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

package io.rover.sdk.seatgeek

import io.rover.sdk.core.data.graphql.safeOptString
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.platform.whenNotNull
import io.rover.sdk.core.privacy.PrivacyService
import org.json.JSONException
import org.json.JSONObject

class SeatGeekManager(
    private val userInfo: UserInfoInterface,
    localStorage: LocalStorage,
    private val privacyService: PrivacyService,
) : SeatGeekAuthorizer, PrivacyService.TrackingEnabledChangedListener {
    private val storage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "seatGeek"
        private const val SEATGEEK_MAP_KEY = "seatGeek"
        // NOTE: seatGeekID is actually the CRM ID ("crmID"). The variable name is maintained for backward compatibility.
        private const val SEATGEEK_ID_KEY = "seatGeekID"
        private const val CLIENT_ID_KEY = "seatGeekClientID"
    }

    @Deprecated(
        "Use setSeatGeekIDs instead"
    )
    override fun setSeatGeekId(crmID: String) {
        if (privacyService.trackingMode != PrivacyService.TrackingMode.Default) {
            log.i("SeatGeek IDs set while privacy is in anonymous/anonymized mode, ignored")
            return
        }
        seatGeekID = crmID
        seatGeekClientID = null
        updateUserInfoWithMemberAttributes()
        log.w("SeatGeek signed in with deprecated CRM ID-only API.")
    }

    override fun setSeatGeekIDs(clientID: String, crmID: String) {
        if (privacyService.trackingMode != PrivacyService.TrackingMode.Default) {
            log.i("SeatGeek IDs set while privacy is in anonymous/anonymized mode, ignored")
            return
        }
        seatGeekID = crmID
        this.seatGeekClientID = clientID
        updateUserInfoWithMemberAttributes()
        log.i("SeatGeek IDs set.")
    }

    private fun updateUserInfoWithMemberAttributes() {
        val localPropertiesMap = getNonNullPropertiesMap()

        // Replace the entire seatGeek map atomically rather than merging, so that
        // null-valued fields don't leave stale entries behind.
        userInfo.update {
            if (localPropertiesMap.isNotEmpty()) {
                it[SEATGEEK_MAP_KEY] = localPropertiesMap
            } else {
                it.remove(SEATGEEK_MAP_KEY)
            }
        }
    }

    override fun clearCredentials() {
        seatGeekID = null
        seatGeekClientID = null
        userInfo.update { it.remove(SEATGEEK_MAP_KEY) }
        log.i("SeatGeek signed out.")
    }

    private var seatGeekID: String?
        get() {
            val storageJson = storage["seatGeekID"]
            return storageJson.whenNotNull { seatGeekIDString ->
                try {
                    JSONObject(seatGeekIDString).safeOptString(SEATGEEK_ID_KEY)
                } catch (e: JSONException) {
                    log.w("Invalid JSON in seatgeek manager storage, ignoring: $e")
                    null
                }
            }
        }
        set(value) {
            if (value != null) {
                storage["seatGeekID"] = JSONObject()
                        .put(SEATGEEK_ID_KEY, value)
                        .toString()
            } else {
                storage["seatGeekID"] = null
            }
        }

    private var seatGeekClientID: String?
        get() {
            val storageJson = storage[CLIENT_ID_KEY]
            return storageJson.whenNotNull { clientIDString ->
                try {
                    JSONObject(clientIDString).safeOptString(CLIENT_ID_KEY)
                } catch (e: JSONException) {
                    log.w("Invalid JSON in seatgeek client ID storage, ignoring: $e")
                    null
                }
            }
        }
        set(value) {
            if (value != null) {
                storage[CLIENT_ID_KEY] = JSONObject()
                    .put(CLIENT_ID_KEY, value)
                    .toString()
            } else {
                storage[CLIENT_ID_KEY] = null
            }
        }

    private fun getNonNullPropertiesMap(): Map<String, String> {
        val propertiesMap = mutableMapOf<String, String>()
        seatGeekID.whenNotNull { propertiesMap.put(SEATGEEK_ID_KEY, it) }
        seatGeekClientID.whenNotNull { propertiesMap.put(CLIENT_ID_KEY, it) }
        return propertiesMap
    }

    override fun onTrackingModeChanged(trackingMode: PrivacyService.TrackingMode) {
        if (trackingMode != PrivacyService.TrackingMode.Default) {
            clearCredentials()
        }
    }
}
