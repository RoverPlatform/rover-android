package io.rover.experiences.data.graphql

/**
 * A network response.  Optionally either a success (with requested payload), or a failure (with the
 * reason why), and whether or not the caller ought to attempt to repeat the request.
 */
internal sealed class ApiResult<T> {
    data class Error<T>(
        val throwable: Throwable,

        /**
         * Indicates if the Rover API recommends that the consumer should attempt to retry.  If
         * true, the error should be considered a "soft failure" for external reasons (network
         * trouble, temporary outages on the cloud-side Rover API gateway, etc.) and the consumer
         * code should attempt a retry after a momentary wait.
         */
        val shouldRetry: Boolean
    ) : ApiResult<T>()

    data class Success<T>(val response: T) : ApiResult<T>()
}

internal sealed class ApiError(
    private val description: String
) : Exception(description) {
    class EmptyResponseData : ApiError("Empty response data")
    class FailedToDecodeResponseData : ApiError("Failed to deserialize response data")
    class InvalidResponse : ApiError("Invalid response")
    class InvalidResponseData(serverMessage: String) : ApiError("Invalid response data: $serverMessage")
    class InvalidStatusCode(statusCode: Int, serverMessage: String) : ApiError("Invalid status code: $statusCode.  Given reason: '$serverMessage'")
    class InvalidURL : ApiError("Invalid URL")

    override fun toString(): String {
        return "ApiError(description=$description)"
    }
}
