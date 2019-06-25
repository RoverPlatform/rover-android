package io.rover.sdk.data.operations

import io.rover.sdk.data.domain.Experience
import io.rover.sdk.data.graphql.GraphQlRequest
import io.rover.sdk.data.operations.data.decodeJson
import org.json.JSONObject

internal class FetchExperienceRequest(
    private val queryIdentifier: ExperienceQueryIdentifier
) : GraphQlRequest<Experience> {
    override val operationName: String = "FetchExperience"

    override val mutation: Boolean
        get() = false

    override val fragments: List<String>
        get() = listOf("experienceFields")

    override val query: String = """
        query FetchExperience(${"\$"}id: ID, ${"\$"}campaignURL: String) {
            experience(id: ${"\$"}id, campaignURL: ${"\$"}campaignURL) {
                ...experienceFields
            }
        }
    """
    override val variables: JSONObject = JSONObject().apply {
        when (queryIdentifier) {
            is ExperienceQueryIdentifier.ById -> {
                put("id", queryIdentifier.id)
                put("campaignURL", JSONObject.NULL)
            }
            is ExperienceQueryIdentifier.ByUniversalLink -> {
                put("id", JSONObject.NULL)
                put("campaignURL", queryIdentifier.uri)
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
        data class ById(val id: String) : ExperienceQueryIdentifier()

        /**
         * Experiences may be started from a universal link.  The link itself may ultimately, but
         * opaquely, address the experience and a possibly associated campaign, but it is up to the
         * cloud API to resolve it.
         *
         * (This method is typically used when experiences are started from external sources,
         * particularly email, social, external apps, and other services integrated into the app).
         */
        data class ByUniversalLink(val uri: String) : ExperienceQueryIdentifier()
    }
}
