package io.rover.rover.services.network.requests

import io.rover.rover.core.domain.Experience
import io.rover.rover.core.domain.ID
import io.rover.rover.platform.whenNotNull
import io.rover.rover.services.network.NetworkRequest
import io.rover.rover.services.network.WireEncoderInterface
import io.rover.rover.services.network.requests.data.getObjectIterable
import io.rover.rover.services.network.requests.data.getStringIterable
import org.json.JSONObject

class FetchExperienceRequest(
    val id: ID
) : NetworkRequest<Experience> {
    override val operationName: String = "FetchExperience"

    override val query: String = """
        query FetchExperience(${"\$id"}: ID!) {
            experience(id: ${"\$id"}) {
                name
            }
        }
    """
    override val variables: JSONObject = JSONObject().apply {
        put("id", id.rawValue)
    }

    override fun decodePayload(responseObject: JSONObject, wireEncoder: WireEncoderInterface): Experience {
        return wireEncoder.decodeExperience(
            responseObject.getJSONObject("data")
        )
    }
}
