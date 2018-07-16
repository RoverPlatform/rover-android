package io.rover.core.data.graphql.operations

import io.rover.core.data.domain.Experience
import io.rover.core.data.GraphQlRequest
import io.rover.core.data.graphql.operations.data.decodeJson
import org.json.JSONObject

class FetchExperienceRequest(
    private val queryIdentifier: ExperienceQueryIdentifier
) : GraphQlRequest<Experience> {
    override val operationName: String = "FetchExperience"

    override val mutation: Boolean
        get() = false

    override val fragments: List<String>
        get() = listOf("experienceFields")

    override val query: String = """
        query FetchExperience(${"\$"}id: ID, ${"\$"}campaignID: ID, ${"\$"}campaignURL: String) {
            experience(id: ${"\$"}id, campaignID: ${"\$"}campaignID, campaignURL: ${"\$"}campaignURL) {
                ...experienceFields
            }
        }
    """
    override val variables: JSONObject = JSONObject().apply {
        when(queryIdentifier) {
            is ExperienceQueryIdentifier.ById -> {
                put("id", queryIdentifier.id)
                put("campaignID", JSONObject.NULL)
                put("campaignURL", JSONObject.NULL)
            }
            is ExperienceQueryIdentifier.ByCampaignId -> {
                put("id", JSONObject.NULL)
                put("campaignID", queryIdentifier.campaignId)
                put("campaignURL", JSONObject.NULL)
            }
            is ExperienceQueryIdentifier.ByUniversalLink -> {
                put("url", queryIdentifier.uri)
                put("id", JSONObject.NULL)
                put("campaignURL", JSONObject.NULL)
            }
        }
    }

    override fun decodePayload(responseObject: JSONObject): Experience {
        return Experience.decodeJson(
            responseObject.getJSONObject("data").getJSONObject("experience")
        )
    }

    sealed class ExperienceQueryIdentifier {
        /**
         * Experiences may be started by just their ID.
         *
         * (This method is typically used when experiences are started from a deep link or
         * progammatically.)
         */
        data class ById(val id: String): ExperienceQueryIdentifier()

        data class ByCampaignId(val campaignId: String): ExperienceQueryIdentifier()

        /**
         * Experiences may be started from a universal link.  The link itself may ultimately, but
         * opaquely, address the experience and a possibly associated campaign, but it is up to the
         * cloud API to resolve it.
         *
         * (This method is typically used when experiences are started from external sources,
         * particularly email, social, external apps, and other services integrated into the app).
         */
        data class ByUniversalLink(val uri: String): ExperienceQueryIdentifier()
    }
}
