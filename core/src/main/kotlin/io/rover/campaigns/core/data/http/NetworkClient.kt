package io.rover.campaigns.core.data.http

import org.reactivestreams.Publisher

interface NetworkClient {
    /**
     * Wen subscribed performs the given [HttpRequest] and then yields the result.
     *
     * Note that the subscriber is given an [HttpClientResponse], which includes readable streams.
     * Thus, it is called on the background worker thread to allow for client code to read those
     * streams, safely away from the Android main UI thread.
     */
    fun request(
        request: HttpRequest,
        bodyData: String?
    ): Publisher<HttpClientResponse>
}
