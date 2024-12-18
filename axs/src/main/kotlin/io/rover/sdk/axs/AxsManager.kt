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

package io.rover.sdk.axs

import io.rover.sdk.core.data.graphql.safeOptString
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.platform.whenNotNull
import io.rover.sdk.core.privacy.PrivacyService
import org.json.JSONException
import org.json.JSONObject

class AxsManager(
    private val userInfo: UserInfoInterface,
    localStorage: LocalStorage,
    private val privacyService: PrivacyService,
) : AxsAuthorizer, PrivacyService.TrackingEnabledChangedListener {
    private val storage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "axs"
        private const val AXS_MAP_KEY = "axs"
        private const val AXS_ID_KEY = "userID"
        private const val FLASH_MEMBER_ID_KEY = "flashMemberID"
        private const val FLASH_MOBILE_ID_KEY = "flashMobileID"
    }

    override fun setUserId(userID: String) {
        setUserId(userID, null, null)
    }

    override fun setUserId(userId: String?, flashMemberId: String?, flashMobileId: String?) {
        if (privacyService.trackingMode != PrivacyService.TrackingMode.Default) {
            return
        }

        if (userId == null) {
            clearCredentials()
            return
        }

        this.axsUserId = userId
        this.flashMemberId = flashMemberId
        this.flashMobileId = flashMobileId

        updateUserInfoWithMemberAttributes()
        log.i("AXS IDs have been set. user ID: $userId, flashMemberID: $flashMemberId, flashMobileID: $flashMobileId")
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateUserInfoWithMemberAttributes() {
        val localPropertiesMap = getNonNullPropertiesMap()

        userInfo.update {
            if (it.containsKey(AXS_MAP_KEY)) {
                val axsAttributes = it.getValue(AXS_MAP_KEY) as MutableMap<String, Any>
                localPropertiesMap.forEach { (propertyName, propertyValue) ->
                    axsAttributes[propertyName] = propertyValue
                }
                it[AXS_MAP_KEY] = axsAttributes
            } else {
                if (localPropertiesMap.isNotEmpty()) it[AXS_MAP_KEY] = localPropertiesMap
            }
        }
    }

    override fun clearCredentials() {
        axsUserId = null
        flashMemberId = null
        flashMobileId = null
        userInfo.update {
            it.remove(AXS_MAP_KEY)
            it.remove(FLASH_MEMBER_ID_KEY)
            it.remove((FLASH_MOBILE_ID_KEY))
        }
        log.i("AXS signed out.")
    }

    private var axsUserId: String?
        get() {
            val storageJson = storage["axs"]
            return storageJson.whenNotNull { axsIDString ->
                try {
                    JSONObject(axsIDString).safeOptString(AXS_ID_KEY)
                } catch (e: JSONException) {
                    log.w("Invalid JSON in AXS manager storage, ignoring: $e")
                    null
                }
            }
        }
        set(value) {
            if (value != null) {
                storage["axs"] = JSONObject()
                        .put(AXS_ID_KEY, value)
                        .toString()
            } else {
                storage["axs"] = null
            }
        }

    private var flashMemberId: String?
        get() {
            val storageJson = storage["axs.flashMemberID"]
            return storageJson.whenNotNull { axsIDString ->
                try {
                    JSONObject(axsIDString).safeOptString(FLASH_MEMBER_ID_KEY)
                } catch (e: JSONException) {
                    log.w("Invalid JSON for FlashMemberID in AXS manager storage, ignoring: $e")
                    null
                }
            }
        }
        set(value) {
            if (value != null) {
                storage["axs.flashMemberID"] = JSONObject()
                    .put(FLASH_MEMBER_ID_KEY, value)
                    .toString()
            } else {
                storage["axs.flashMemberID"] = null
            }
        }

    private var flashMobileId: String?
        get() {
            val storageJson = storage["axs.flashMobileID"]
            return storageJson.whenNotNull { axsIDString ->
                try {
                    JSONObject(axsIDString).safeOptString(FLASH_MOBILE_ID_KEY)
                } catch (e: JSONException) {
                    log.w("Invalid JSON for FlashMobileID in AXS manager storage, ignoring: $e")
                    null
                }
            }
        }
        set(value) {
            if (value != null) {
                storage["axs.flashMobileID"] = JSONObject()
                    .put(FLASH_MOBILE_ID_KEY, value)
                    .toString()
            } else {
                storage["axs.flashMobileID"] = null
            }
        }

    private fun getNonNullPropertiesMap(): Map<String, String> {
        val propertiesMap = mutableMapOf<String, String>()
        axsUserId.whenNotNull { propertiesMap.put(AXS_ID_KEY, it)}
        flashMemberId.whenNotNull { propertiesMap.put(FLASH_MEMBER_ID_KEY, it)}
        flashMobileId.whenNotNull { propertiesMap.put(FLASH_MOBILE_ID_KEY, it)}
        return propertiesMap
    }

    override fun onTrackingModeChanged(trackingMode: PrivacyService.TrackingMode) {
        if (trackingMode != PrivacyService.TrackingMode.Default) {
            clearCredentials()
        }
    }
}
