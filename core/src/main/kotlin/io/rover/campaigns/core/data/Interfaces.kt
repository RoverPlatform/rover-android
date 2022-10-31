package io.rover.campaigns.core.data

import io.rover.campaigns.core.data.graphql.getObjectIterable
import org.json.JSONArray
import org.json.JSONObject

/**
 * A network response.  Optionally either a success (with requested payload), or a failure (with the
 * reason why, and whether or not the caller ought to attempt to repeat the request.
 */
sealed class NetworkResult<T> {
    data class Error<T>(
        val throwable: Throwable,

        /**
         * Indicates if the Rover API recommends that the consumer should attempt to retry.  If
         * true, the error should be considered a "soft failure" for external reasons (network
         * trouble, temporary outages on the cloud-side Rover API gateway, etc.) and the consumer
         * code should attempt a retry after a momentary wait.
         */
        val shouldRetry: Boolean
    ) : NetworkResult<T>()

    data class Success<T>(val response: T) : NetworkResult<T>()
}

sealed class NetworkError(
    private val description: String
) : Exception(description) {
    class EmptyResponseData : NetworkError("Empty response data")
    class FailedToDecodeResponseData : NetworkError("Failed to deserialize response data")
    class InvalidResponse : NetworkError("Invalid response")
    class InvalidResponseData(serverMessage: String) : NetworkError("Invalid response data: $serverMessage")
    class InvalidStatusCode(statusCode: Int, serverMessage: String) : NetworkError("Invalid status code: $statusCode.  Given reason: '$serverMessage'")
    class InvalidURL : NetworkError("Invalid URL")

    override fun toString(): String {
        return "NetworkError(description=$description)"
    }
}

/**
 * A Rover GraphQL API-flavored network request.
 *
 * @param TInput This is the type of the reply you expect to arrive back from the cloud API.
 */
interface GraphQlRequest<out TInput> {
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
     * behaviour.  If not, we'll use a GET verb.
     */
    val mutation: Boolean
        get() = true

    /**
     * GraphQL query string.
     */
    val query: String

    val variables: JSONObject

    /**
     * If you are using any fragments provided GraphQL Gateway, specify them in your override of
     * this.
     */
    val fragments: List<String> get() = listOf()

    /**
     * Unpack the given response body, interpreting the GraphQL response envelope and yielding any
     * API-side errors as [APIException].
     *
     * A default implementation is provided here, which should suffice for most use cases.
     */
    fun decode(json: String): TInput {
        val parsed = JSONObject(json)
        val possibleErrors = parsed.optJSONArray("errors")
        if (possibleErrors != null) {
            throw APIException(possibleErrors.getObjectIterable().map {
                // TODO: change to a better type than just Exception.  perhaps one with best-effort decoding of the GraphQL errors object.
                Exception(it.toString())
            })
        } else {
            return decodePayload(parsed)
        }
    }

    /**
     * Implement this method to provide a method for encoding the inbound response from the backend.
     *
     * You are given the full [JSONObject] for the payload, however, you need not check for the
     * `errors` list.  The only reason you are given the full payload object is because only
     * implementers of GraphQlRequest will know whether the `data` item will be an object, array,
     * string, or what have you, and a peculiarity of the org.json package is that you more-or-less
     * need to know ahead of time what you are expecting.
     */
    fun decodePayload(responseObject: JSONObject): TInput

    /**
     * Implements the standard outgoing query request envelope format in request body form, if a mutation.
     *
     * Will return an empty body if it a non-mutation operation, which will use HTTP GET instead of
     * POST and thus does not use a request body.
     */
    fun encodeBody(): String? {
        return if (mutation) {
            JSONObject().apply {
                put("variables", variables)
                put("fragments", fragments)
                put("query", query)
                if (operationName != null) {
                    put("operationName", operationName)
                }
            }.toString()
        } else null
    }

    /**
     * Implements the standard outgoing query request envelope format, if a mutation.
     */
    fun encodeQueryParameters(): Map<String, String> {
        return if (!mutation) {
            hashMapOf(
                Pair("variables", variables.toString()),
                Pair("fragments", JSONArray(fragments).toString()),
                Pair("query", query)
            )
        } else hashMapOf()
    }
}

class APIException(
    val errors: List<Exception>
) : Exception("Rover API reported: ${errors.map { it.message }.joinToString(", ")}")
