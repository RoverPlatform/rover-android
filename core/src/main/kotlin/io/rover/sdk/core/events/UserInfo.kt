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

package io.rover.sdk.core.events

import io.rover.sdk.core.data.domain.Attributes
import io.rover.sdk.core.data.graphql.operations.data.encodeJson
import io.rover.sdk.core.data.graphql.operations.data.toAttributesHash
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.privacy.PrivacyService
import org.json.JSONObject

class UserInfo(
    localStorage: LocalStorage,
    private val privacyService: PrivacyService
) : UserInfoInterface, PrivacyService.TrackingEnabledChangedListener {
    private val store = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)
    override fun update(builder: (attributes: HashMap<String, Any>) -> Unit) {
        val mutableDraft = HashMap(currentUserInfo)
        builder(mutableDraft)
        currentUserInfo = mutableDraft
        reportedUserInfoMayHaveChanged()
    }

    override fun clear() {
        currentUserInfo = hashMapOf()
        reportedUserInfoMayHaveChanged()
    }

    override val reportedUserInfo: Attributes
        get() = filterUserInfoForPrivacy(currentUserInfo, privacyService.trackingMode)

    /**
     * The stored user info is never touched by a tracking mode change, but the reported view of
     * it just gained or lost its sensitive keys.
     */
    override fun onTrackingModeChanged(trackingMode: PrivacyService.TrackingMode) {
        reportedUserInfoMayHaveChanged()
    }

    /**
     * Called whenever [reportedUserInfo] may have changed, either because the stored value was
     * updated or because the tracking mode moved. Whether it actually changed is up to whoever
     * cares; this is deliberately called even when nothing moved, because both the tracking mode
     * setter and listener registration notify unconditionally.
     */
    private fun reportedUserInfoMayHaveChanged() {
        log.v("Reported user info may have changed.")
    }

    override var currentUserInfo: Attributes = try {
        val currentAttributesJson = store[USER_INFO_KEY]
        when (currentAttributesJson) {
            null -> hashMapOf()
            else -> JSONObject(store[USER_INFO_KEY]).toAttributesHash()
        }
    } catch (throwable: Throwable) {
        log.w("Corrupted local user info, ignoring and starting fresh.  Cause: ${throwable.message}")
        hashMapOf()
    }
        private set(value) {
            field = value
            store[USER_INFO_KEY] = value.encodeJson().toString()
            log.v("Stored new user info.")
        }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "user-info"
        private const val USER_INFO_KEY = "user-info"
    }
}
