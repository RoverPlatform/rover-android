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
import org.json.JSONObject

class UserInfo(
    localStorage: LocalStorage
) : UserInfoInterface {
    private val store = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)
    override fun update(builder: (attributes: HashMap<String, Any>) -> Unit) {
        val mutableDraft = HashMap(currentUserInfo)
        builder(mutableDraft)
        currentUserInfo = mutableDraft
    }

    override fun clear() {
        currentUserInfo = hashMapOf()
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
