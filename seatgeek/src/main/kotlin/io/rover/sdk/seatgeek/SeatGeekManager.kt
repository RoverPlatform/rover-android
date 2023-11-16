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
        private const val SEATGEEK_ID_KEY = "seatGeekID"
    }

    override fun setSeatGeekId(crmID: String) {
        if (privacyService.trackingMode != PrivacyService.TrackingMode.Default) {
            return
        }
        seatGeekID = crmID
        updateUserInfoWithMemberAttributes()
        log.i("SeatGeek signed in with '$crmID'.")
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateUserInfoWithMemberAttributes() {
        val localPropertiesMap = getNonNullPropertiesMap()

        userInfo.update {
            if (it.containsKey(SEATGEEK_MAP_KEY)) {
                val sgAttributes = it.getValue(SEATGEEK_MAP_KEY) as MutableMap<String, Any>
                localPropertiesMap.forEach { (propertyName, propertyValue) ->
                    sgAttributes[propertyName] = propertyValue
                }
                it[SEATGEEK_MAP_KEY] = sgAttributes
            } else {
                if (localPropertiesMap.isNotEmpty()) it[SEATGEEK_MAP_KEY] = localPropertiesMap
            }
        }
    }

    override fun clearCredentials() {
        seatGeekID = null
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

    private fun getNonNullPropertiesMap(): Map<String, String> {
        val propertiesMap = mutableMapOf<String, String>()
        seatGeekID.whenNotNull { propertiesMap.put(SEATGEEK_ID_KEY, it)}
        return propertiesMap
    }

    override fun onTrackingModeChanged(trackingMode: PrivacyService.TrackingMode) {
        if (trackingMode != PrivacyService.TrackingMode.Default) {
            clearCredentials()
        }
    }
}
