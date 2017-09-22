package io.rover.rover.services.network.requests

import io.rover.rover.services.network.NetworkRequest
import org.json.JSONObject


data class ExperienceParameters(
    val id: String
)

data class ExperiencePayload(
    val id: String
    // val homescreen: HomeScreen
)

class ExperienceRequest: NetworkRequest<ExperienceParameters, ExperiencePayload> {
    override fun mapParametersPayload(parameters: ExperienceParameters): String {
        return JSONObject().apply {
            put("id", parameters.id)
        }.toString(4)
    }

    override fun mapOutputPayload(output: String): ExperiencePayload {
        val json = JSONObject(output)
        return ExperiencePayload(
            json.getString("id")
        )
    }

    override val graphQLQuery: String = """
        query FetchExperience($id: ID!) {
            experience(id: $id) {
                name
            }
        }
    """
}
