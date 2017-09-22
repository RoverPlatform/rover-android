package io.rover.rover.services.network

import io.rover.rover.core.domain.Experience
import io.rover.rover.services.concurrency.Scheduler
import io.rover.rover.services.concurrency.Single
import io.rover.rover.services.network.requests.ExperienceParameters
import io.rover.rover.services.network.requests.ExperiencePayload
import io.rover.rover.services.network.requests.ExperienceRequest
import java.io.BufferedReader
import java.net.URL


/**
 * Responsible for network access to the Rover API Gateway in the cloud.
 *
 *
 */
interface NetworkServiceContract {
    // There are four requests.
    fun fetchExperience(id: String): Single<Experience>

//    fun fetchState()
//
//    fun sendEvents()
}

/**
 * A concrete implementation of NetworkServiceContract powered by Rover's GraphQL cloud API.
 */
class GraphQLNetworkService(
    private val baseURL: String,
    private val httpClient: HttpClient,
    private val backgroundScheduler: Scheduler
)  : NetworkServiceContract {

    private val endpoint = URL("$baseURL/graphql")

    // See http://graphql.org/learn/serving-over-http/

    // Concerns:
    // Basic graphql query/mutation semantics and envelopes.
    // identification/auth headers.
    // content-type/encoding negotiation.
    // retry policy mechanism.
    // entry points to our 4 requests (see requests  package). this could potench be factored out
    // to another service.

    override fun fetchExperience(id: String): Single<Experience> {
        return doRequest<ExperienceParameters, ExperiencePayload>(ExperienceRequest()).map(backgroundScheduler) {
            // shitty transform time
            Experience(
                it.id
            )
        }
    }

    private fun <P, O> doRequest(request: NetworkRequest<P, O>): Single<O> {
        // implement the retry behaviour?  will need flatMap() for that.

        // TODO: retry count?
        return httpClient.post(
            endpoint,
            hashMapOf(),
            request.graphQLQuery,
            backgroundScheduler
        ).map(backgroundScheduler) { networkResponse ->
            when(networkResponse) {
            // TODO: maybe buffer and read the entire thing and yield that?
                is HttpClientResponse.Success -> networkResponse.bufferedInputStream.bufferedReader().readText()
                is HttpClientResponse.ConnectionFailure -> {
                    throw RuntimeException("Rover doesn't have a great error handling story yet: connection failure.")
                }
                is HttpClientResponse.ApplicationError -> {
                    throw RuntimeException("Rover doesn't have a great error handling story yet: application error.")
                }
            }
        }.map(backgroundScheduler) {
            request.mapOutputPayload(it)
        }
    }
}
