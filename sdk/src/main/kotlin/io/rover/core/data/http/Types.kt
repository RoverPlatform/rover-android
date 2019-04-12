package io.rover.core.data.http

import java.io.BufferedInputStream
import java.net.URL

enum class HttpVerb(
    val wireFormat: String
) {
    GET("GET"), POST("POST"), PUT("PUT"), DELETE("DELETE")
}

data class HttpRequest(
    val url: URL,
    val headers: HashMap<String, String>,
    val verb: HttpVerb
)

sealed class HttpClientResponse {
    class Success(
        /**
         * The HTTP request has gotten a successful reply, and now the server is streaming the
         * response body to us.
         *
         * Remember to close the stream after completing reading from it!
         */
        val bufferedInputStream: BufferedInputStream
    ) : HttpClientResponse()

    /**
     * A a session layer or below onError (HTTP protocol onError, network onError, and so on)
     * occurred. Likely culprit is local connectivity issues or possibly even a Rover API outage.
     */
    class ConnectionFailure(val reason: Throwable) : HttpClientResponse()

    /**
     * An application layer (HTTP) onError occurred (ie., a non-2xx status code).
     */
    class ApplicationError(
        val responseCode: Int,
        val reportedReason: String
    ) : HttpClientResponse()
}