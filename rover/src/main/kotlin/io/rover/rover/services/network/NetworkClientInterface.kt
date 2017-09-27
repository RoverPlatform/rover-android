package io.rover.rover.services.network

import android.os.AsyncTask
import java.io.BufferedInputStream
import java.net.URL

/**
 * A cancellable concurrent operation.
 */
interface NetworkTask {
    fun cancel()
    fun resume()
}

data class HttpRequest(
    val url: URL,
    val headers: HashMap<String, String>
)

interface NetworkClient {
    fun networkTask(request: HttpRequest, bodyData: String?, completionHandler: (HttpClientResponse) -> Unit): NetworkTask
}

class AsyncTaskNetworkTask(
    private val asyncTask: AsyncTask<*, *, *>
): NetworkTask {
    override fun cancel() {
        asyncTask.cancel(false)
    }

    override fun resume() {
        asyncTask.execute()
    }
}

sealed class HttpClientResponse {
    class Success(
        /**
         * The HTTP request has gotten a successful reply, and now the server is streaming the
         * response body to us.
         */
        val bufferedInputStream: BufferedInputStream
    ) : HttpClientResponse()

    /**
     * A a session layer or below onError (HTTP protocol onError, network onError, and so on) occurred.
     * Likely culprit is local connectivity issues or possibly even a Rover API outage.
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