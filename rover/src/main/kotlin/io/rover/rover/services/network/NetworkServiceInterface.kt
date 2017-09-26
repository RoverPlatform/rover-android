package io.rover.rover.services.network

import io.rover.rover.core.domain.Experience
import io.rover.rover.core.domain.ID

sealed class NetworkResult<T> {
    class Error<T>(val throwable: Throwable, val shouldRetry: Boolean): NetworkResult<T>()
    class Success<T>(val response: T): NetworkResult<T>()
}

sealed class NetworkError(
    description: String
): Exception(description) {
    class EmptyResponseData: NetworkError("Empty response data")
    class FailedToDecodeResponseData : NetworkError("Failed to deserialized response data")
    class InvalidResponse : NetworkError("Invalid response")
    class InvalidResponseData(val serverMessage: String): NetworkError("Invalid response data: $serverMessage")
    class InvalidStatusCode(val statusCode: Int): NetworkError("Invalid status code: $statusCode")
    class InvalidURL : NetworkError("Invalid URL")
}

interface NetworkRequest {
    // JSON encode/decode interface concern needs to go in here

    val operationName: String?
    val query: String
    val variables: HashMap<String, String>

    // TODO: all concerns that must be handled
    // Parameters
    // graphql request type (query/mutation)
    // GraphQL query DSL (multiline string constant).
    // its parameters/data type thereof (DTO)
    // its output/data type thereof (DTO)
    // mapping to domain types (this may be appropriately put elsewhere; decide later)
    // the boilerplate of JSON field mapping (since we don't get to use gson or have any
    // other equivalent to Swift's Encodable/Decodable.
}

interface NetworkServiceInterface {
    var profileIdentifier: String?

    fun fetchExperienceTask(experienceID: ID, completionHandler: ((NetworkResult<Experience>) -> Unit)?): NetworkTask
//    fun fetchStateTask(completionHandler: ((NetworkResult<State>) -> Unit)?): NetworkTask
//    fun sendEventsTask(events: List<Event>, context: Context, profileIdentifier: String?, completionHandler: ((NetworkResult<String>) -> Void)?): NetworkTask
}
