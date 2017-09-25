package io.rover.rover.services.network

import io.rover.rover.core.domain.Experience
import io.rover.rover.core.logging.log
import io.rover.rover.services.concurrency.Scheduler
import io.rover.rover.services.concurrency.Single
import io.rover.rover.services.network.requests.ExperienceRequest
import java.net.URL

sealed class NetworkResult<T> {
    class Error<T>(val throwable: Throwable, val shouldRetry: Boolean): NetworkResult<T>()
    class Success<T>(val response: T): NetworkResult<T>()
}

/**
 * Responsible for network access to the Rover API Gateway in the cloud.
 *
 *
 */
interface NetworkServiceInterface {
    // There are four requests.

    /**
     *
     */
    fun fetchExperience(id: String): Single<NetworkResult<Experience>>

//    fun fetchState()
//
//    fun sendEvents()
}

/**
 * A concrete implementation of [NetworkServiceInterface] powered by Rover's GraphQL cloud API.
 */
class NetworkService(
    private val baseURL: String,
    private val httpClient: HttpClient,
    private val backgroundScheduler: Scheduler
)  : NetworkServiceInterface {

    private val endpoint = URL("$baseURL/graphql")

    // See http://graphql.org/learn/serving-over-http/

    // Concerns:
    // Basic graphql query/mutation semantics and envelopes.
    // identification/auth headers.
    // content-type/encoding negotiation.
    // retry policy mechanism.
    // entry points to our 4 requests (see requests  package). this could potench be factored out
    // to another service.

    override fun fetchExperience(id: String): Single<NetworkResult<Experience>> {
        log.d("Fetching experience with id $id")
        return doRequest(ExperienceRequest()).map(backgroundScheduler) {
            log.d("Received experience from the Rover API, with ID $id")
            NetworkResult.Success(
                Experience(
                    it.id
                )
            )
        }
    }

    private fun <P, O> doRequest(request: NetworkRequest<P, O>): Single<O> {
        // implement the retry behaviour?  will need flatMap() for that.

        // TODO: retry count?
        return httpClient.post(
            endpoint,
            hashMapOf(),
            request.graphQLQuery
        ).map(backgroundScheduler) { networkResponse ->
            when(networkResponse) {
            // TODO: maybe buffer and read the entire thing and yield that?
                is HttpClientResponse.Success -> networkResponse.bufferedInputStream.bufferedReader().readText()
                is HttpClientResponse.ConnectionFailure -> {
                    throw RuntimeException("Rover doesn't have a great onError handling story yet: connection failure.")
                }
                is HttpClientResponse.ApplicationError -> {
                    throw RuntimeException("Rover doesn't have a great onError handling story yet: application onError: code ${networkResponse.responseCode} - ${networkResponse.reportedReason}/")
                }
            }
        }.map(backgroundScheduler) {
            request.mapOutputPayload(it)
        }
    }
}
