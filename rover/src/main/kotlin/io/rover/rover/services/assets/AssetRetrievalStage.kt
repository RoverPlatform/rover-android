package io.rover.rover.services.assets

import io.rover.rover.services.network.HttpClientResponse
import io.rover.rover.services.network.HttpRequest
import io.rover.rover.services.network.HttpVerb
import io.rover.rover.services.network.NetworkClient
import io.rover.rover.services.network.NetworkTask
import java.io.BufferedInputStream
import java.net.URL
import java.util.concurrent.CountDownLatch

/**
 * Stream the asset from a remote HTTP API.
 *
 * This never faults to anything further down in the pipeline; it always retrieves from the API.
 */
class AssetRetrievalStage(
    private val networkClient: NetworkClient
) : SynchronousPipelineStage<URL, BufferedInputStream> {
    override fun request(input: URL): BufferedInputStream {
        // so now I am going to just *block* while waiting for the callback.
        return blockWaitForNetworkTask { completionHandler ->
            networkClient.networkTask(
                HttpRequest(input, hashMapOf(), HttpVerb.GET),
                null,
                completionHandler
            )
        }
    }
}

internal fun blockWaitForNetworkTask(invocation: (completionHandler: (HttpClientResponse) -> Unit) -> NetworkTask): BufferedInputStream {
    val latch = CountDownLatch(1)
    var returnStream: BufferedInputStream? = null
    invocation { clientResponse ->
        returnStream = when (clientResponse) {
            is HttpClientResponse.ConnectionFailure -> {
                throw RuntimeException("Network or HTTP error downloading asset", clientResponse.reason)
            }
            is HttpClientResponse.ApplicationError -> {
                throw RuntimeException("Remote HTTP API error downloading asset (code ${clientResponse.responseCode}): ${clientResponse.reportedReason}")
            }
            is HttpClientResponse.Success -> {
                clientResponse.bufferedInputStream
            }
        }
        latch.countDown()
    }.resume()
    // we rely on the network task to handle network timeout for us, so we'll just wait
    // patiently indefinitely here.
    latch.await()

    return returnStream!!
}
