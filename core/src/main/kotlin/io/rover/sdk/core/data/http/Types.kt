/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.core.data.http

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
