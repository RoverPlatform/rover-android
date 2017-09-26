package io.rover.rover.services.network.requests

import io.rover.rover.services.network.NetworkRequest


// DTO marshalling WIP:
//
//data class ExperienceParameters(
//    val id: String
//)
//
//data class ExperiencePayload(
//    val id: String
//    // val homescreen: HomeScreen
//)
//
//class FetchExperienceRequest: HttpRequest<ExperienceParameters, ExperiencePayload> {
//    override fun mapParametersPayload(parameters: ExperienceParameters): String {
//        return JSONObject().apply {
//            put("id", parameters.id)
//        }.toString(4)
//    }
//
//    override fun mapOutputPayload(output: String): ExperiencePayload {
//        val json = JSONObject(output)
//        return ExperiencePayload(
//            json.getString("id")
//        )
//    }
//
//    override val graphQLQuery: String = """
//        query FetchExperience(${"\$id"}: ID!) {
//            experience(id: ${"\$id"}) {
//                name
//            }
//        }
//    """
//}

class FetchExperienceRequest : NetworkRequest {
    override val operationName: String = "FetchExperience"

    override val query: String = """
        query FetchExperience(${"\$id"}: ID!) {
                experience(id: ${"\$id"}) {
                    name
                }
            }
    """
    override val variables: HashMap<String, String>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
}