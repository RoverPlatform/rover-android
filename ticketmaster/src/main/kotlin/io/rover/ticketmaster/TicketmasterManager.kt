package io.rover.ticketmaster

import io.rover.core.data.domain.AttributeValue
import io.rover.core.data.domain.Attributes
import io.rover.core.data.graphql.operations.data.toAttributesHash
import io.rover.core.data.graphql.putProp
import io.rover.core.data.graphql.safeGetString
import io.rover.core.data.graphql.safeOptString
import io.rover.core.data.sync.SyncParticipant
import io.rover.core.data.sync.SyncQuery
import io.rover.core.data.sync.SyncRequest
import io.rover.core.data.sync.SyncResult
import io.rover.core.events.UserInfoInterface
import io.rover.core.logging.log
import io.rover.core.platform.LocalStorage
import io.rover.core.platform.whenNotNull
import org.json.JSONException
import org.json.JSONObject

class TicketmasterManager(
    private val userInfo: UserInfoInterface,
    localStorage: LocalStorage
) : TicketmasterAuthorizer, SyncParticipant {
    private val storage = localStorage.getKeyValueStorageFor("io.rover.ticketmaster.TicketmasterManager")

    override fun setCredentials(backendNameOrdinal: Int, memberId: String?) {

        val backendName = TicketmasterBackendName.values()[backendNameOrdinal]

        member = Member(
            hostID = if(backendName == TicketmasterBackendName.HOST) memberId else null,
            teamID = if(backendName == TicketmasterBackendName.ARCHTICS) memberId else null
        )
    }

    override fun clearCredentials() {
        member = null
        userInfo.update { it.remove("ticketmaster") }
    }

    override fun initialRequest(): SyncRequest? {
        return member.whenNotNull { member ->

            val params = listOfNotNull(
                member.hostID.whenNotNull { Pair("hostMemberID", AttributeValue.Scalar.String(it)) },
                member.teamID.whenNotNull { Pair("teamMemberID", AttributeValue.Scalar.String(it)) }
            )

            if(params.isEmpty()) {
                null
            } else {
                SyncRequest(
                    SyncQuery.ticketmaster,
                    variables = params.associate { it }
                )
            }
        }
    }

    override fun saveResponse(json: JSONObject): SyncResult {
        return try {
            val profileAttributes = TicketmasterSyncResponseData.decodeJson(json.getJSONObject("data")).ticketmasterProfile.attributes

            profileAttributes.whenNotNull { attributes ->
                userInfo.update { userInfo ->
                    userInfo["ticketmaster"] = AttributeValue.Object(attributes)
                }
                SyncResult.NewData(null)
            } ?: SyncResult.NoData
        } catch (e: JSONException) {
            log.v("Unable to parse ticketmaster profile data from Rover API, ignoring: $e")
            SyncResult.Failed
        }
    }

    private var member: Member?
        get() {
            return storage["member"].whenNotNull { memberString ->
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
        val hostID: String?,
        val teamID: String?
    ) {
        companion object
    }

    enum class TicketmasterBackendName {
        HOST, ARCHTICS
    }
}

val SyncQuery.Argument.Companion.hostMemberId
    get() = SyncQuery.Argument("hostMemberID", "String")

val SyncQuery.Argument.Companion.teamMemberID
    get() = SyncQuery.Argument("teamMemberID", "String")


val SyncQuery.Companion.ticketmaster
    get() = SyncQuery(
        "ticketmasterProfile",
        """
            attributes
        """.trimIndent(),
        listOf(SyncQuery.Argument.hostMemberId, SyncQuery.Argument.teamMemberID),
        listOf()
    )

fun TicketmasterManager.Member.Companion.decodeJson(json: JSONObject): TicketmasterManager.Member {
    return TicketmasterManager.Member(
        hostID = json.safeOptString("hostID"),
        teamID = json.safeOptString("teamID")
    )
}

fun TicketmasterManager.Member.encodeJson(): JSONObject {
    return JSONObject().apply {
        listOf(TicketmasterManager.Member::hostID, TicketmasterManager.Member::teamID).forEach {
            putProp(this@encodeJson, it)
        }
    }
}

class TicketmasterSyncResponseData(
    val ticketmasterProfile: Profile
) {
    data class Profile(
        val attributes: Attributes?
    ) {
        companion object
    }

    companion object
}

fun TicketmasterSyncResponseData.Companion.decodeJson(json: JSONObject): TicketmasterSyncResponseData {
    return TicketmasterSyncResponseData(
        TicketmasterSyncResponseData.Profile.decodeJson(json.getJSONObject("ticketmasterProfile"))
    )
}

fun TicketmasterSyncResponseData.Profile.Companion.decodeJson(json: JSONObject): TicketmasterSyncResponseData.Profile {
    return TicketmasterSyncResponseData.Profile(
        json.optJSONObject("attributes")?.toAttributesHash()
    )
}

