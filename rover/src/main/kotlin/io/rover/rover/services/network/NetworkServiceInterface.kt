package io.rover.rover.services.network

import io.rover.rover.core.domain.Context
import io.rover.rover.core.domain.DeviceState
import io.rover.rover.core.domain.Event
import io.rover.rover.core.domain.Experience
import io.rover.rover.core.domain.ID
import org.json.JSONObject

/**
 * Rover GraphQL API-flavored network response.
 */
sealed class NetworkResult<T> {
    class Error<T>(
        val throwable: Throwable,

        /**
         * Does the Rover API recommend that the consumer should attempt to retry.  If true,
         * the error should be considered a "soft failure" for external reasons (network trouble,
         * temporary outages on the cloud-side Rover API gateway, etc.) and the consumer code
         * should attempt a retry after a momentary wait.
         */
        val shouldRetry: Boolean
    ) : NetworkResult<T>()

    class Success<T>(val response: T) : NetworkResult<T>()
}

sealed class NetworkError(
    description: String
) : Exception(description) {
    class EmptyResponseData : NetworkError("Empty response data")
    class FailedToDecodeResponseData : NetworkError("Failed to deserialize response data")
    class InvalidResponse : NetworkError("Invalid response")
    class InvalidResponseData(val serverMessage: String) : NetworkError("Invalid response data: $serverMessage")
    class InvalidStatusCode(val statusCode: Int, val serverMessage: String) : NetworkError("Invalid status code: $statusCode.  Given reason: '$serverMessage'")
    class InvalidURL : NetworkError("Invalid URL")
}

/**
 * A Rover GraphQL API-flavored network request.
 *
 * @param TInput This is the type of the reply you expect to arrive back from the cloud API.
 */
interface NetworkRequest<out TInput> {
    /**
     * A GraphQL operation name that should be selected out of the query.  Optional.
     */
    val operationName: String?
        get() = null

    /**
     * Does this request expect to change the state of the remote of the API (or anything in
     * the larger world at large)?
     *
     * If so, then we will submit the GraphQL query with a POST verb and opt-out of caching
     * behaviour.
     */
    val mutation: Boolean
        get() = false

    /**
     * GraphQL query string.
     */
    val query: String

    val variables: JSONObject

    fun decode(json: String, wireEncoder: WireEncoderInterface): TInput {
        val parsed = JSONObject(json)
        val possibleErrors = parsed.optJSONArray("errors")
        if (possibleErrors != null) {
            throw APIException(wireEncoder.decodeErrors(possibleErrors))
        } else {
            return decodePayload(parsed, wireEncoder)
        }
    }

    /**
     * Implement this method to provide a method for encoding the inbound response from the backend.
     *
     * You are given the full [JSONObject] for the payload, however, you need not check for the
     * `errors` list.  The only reason you are given the full payload object is because only
     * implementers of NetworkRequest will know whether the `data` item will be an object, array,
     * string, or what have you, and a peculiarity of the org.json package is that you more-or-less
     * need to know ahead of time what you are expecting.
     */
    fun decodePayload(responseObject: JSONObject, wireEncoder: WireEncoderInterface): TInput

    /**
     * Implements the standard outgoing query request envelope format.
     */
    fun encode(): String {
        return JSONObject().apply {
            put("variables", variables)
            put("query", query)
            if (operationName != null) {
                put("operationName", operationName)
            }
        }.toString(4)
    }
}

interface NetworkServiceInterface {
    var profileIdentifier: String?

    fun fetchExperienceTask(experienceID: ID, completionHandler: ((NetworkResult<Experience>) -> Unit)): NetworkTask

    fun fetchStateTask(completionHandler: ((NetworkResult<DeviceState>) -> Unit)): NetworkTask

    fun sendEventsTask(events: List<Event>, context: Context, profileIdentifier: String?, completionHandler: ((NetworkResult<String>) -> Unit)): NetworkTask
}

class APIException(
    val errors: List<Exception>
) : Exception("Rover API reported: ${errors.map { it.message }.joinToString(", ")}")
