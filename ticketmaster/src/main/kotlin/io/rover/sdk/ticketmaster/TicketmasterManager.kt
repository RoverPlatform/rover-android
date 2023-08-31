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

package io.rover.sdk.ticketmaster

import android.content.Context
import io.rover.sdk.core.data.graphql.putProp
import io.rover.sdk.core.data.graphql.safeOptString
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.platform.whenNotNull
import org.json.JSONException
import org.json.JSONObject

class TicketmasterManager(
    private val userInfo: UserInfoInterface,
    localStorage: LocalStorage
) : TicketmasterAuthorizer {
    private val storage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "ticketmaster"
        private const val TICKETMASTER_MAP_KEY = "ticketmaster"
    }

    override fun setTicketmasterId(id: String) {
        member = Member(
            ticketmasterID = id,
            email = null,
            firstName = null
        )
        updateUserInfoWithMemberAttributes()
        log.i("Ticketmaster signed in with '$id'.")
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateUserInfoWithMemberAttributes() {
        val localPropertiesMap = member?.getNonNullPropertiesMap()

        userInfo.update {
            if (it.containsKey(TICKETMASTER_MAP_KEY)) {
                val tmAttributes = it.getValue(TICKETMASTER_MAP_KEY) as MutableMap<String, Any>
                localPropertiesMap?.forEach { (propertyName, propertyValue) ->
                    tmAttributes[propertyName] = propertyValue
                }
                it[TICKETMASTER_MAP_KEY] = tmAttributes
            } else {
                if (localPropertiesMap?.isNotEmpty() == true) it[TICKETMASTER_MAP_KEY] = localPropertiesMap
            }
        }
    }

    override fun clearCredentials() {
        member = null
        userInfo.update { it.remove(TICKETMASTER_MAP_KEY) }
        log.i("Ticketmaster signed out.")
    }

    private var member: Member?
        get() {
            val storageJson = storage["member"]
            return storageJson.whenNotNull { memberString ->
                try {
                    Member.decodeJson(JSONObject(memberString))
                } catch (e: JSONException) {
                    log.w("Invalid JSON in ticketmaster manager storage, ignoring: $e")
                    null
                }
            }
        }
        set(value) {
            storage["member"] = value?.encodeJson().toString()
        }

    data class Member(
        val ticketmasterID: String?,
        val email: String?,
        val firstName: String?
    ) {
        companion object

        fun getNonNullPropertiesMap(): Map<String, String> {
            val propertiesMap = mutableMapOf<String, String>()
            ticketmasterID.whenNotNull { propertiesMap.put(Member::ticketmasterID.name, it)}
            email.whenNotNull { propertiesMap.put(Member::email.name, it) }
            firstName.whenNotNull { propertiesMap.put(Member::firstName.name, it) }
            return propertiesMap
        }
    }
}

fun TicketmasterManager.Member.Companion.decodeJson(json: JSONObject): TicketmasterManager.Member {
    return TicketmasterManager.Member(
        ticketmasterID = json.safeOptString(TicketmasterManager.Member::ticketmasterID.name)
            ?: json.safeOptString("hostID")
            ?: json.safeOptString("teamID"),
        email = json.safeOptString(TicketmasterManager.Member::email.name),
        firstName = json.safeOptString(TicketmasterManager.Member::firstName.name)
    )
}

fun TicketmasterManager.Member.encodeJson(jsonObject: JSONObject = JSONObject()): JSONObject {
    return jsonObject.apply {
        listOf(
            TicketmasterManager.Member::ticketmasterID,
            TicketmasterManager.Member::email,
            TicketmasterManager.Member::firstName
        ).forEach {
            putProp(this@encodeJson, it)
        }
    }
}
