package io.rover.core.data.graphql

import io.rover.core.data.GraphQlRequest
import io.rover.core.data.NetworkResult
import io.rover.core.data.domain.EventSnapshot
import io.rover.core.data.domain.Experience
import io.rover.core.data.graphql.operations.FetchExperienceRequest
import org.reactivestreams.Publisher

interface GraphQlApiServiceInterface {
    /**
     * Retrieves the experience when subscribed and yields it to the subscriber.
     */
    fun fetchExperience(
        query: FetchExperienceRequest.ExperienceQueryIdentifier
    ): Publisher<NetworkResult<Experience>>

    /**
     * Performs the given [GraphQlRequest] when subscribed and yields the result to the subscriber.
     */
    fun <TEntity> operation(
        request: GraphQlRequest<TEntity>
    ): Publisher<NetworkResult<TEntity>>

    /**
     * Submit analytics events when subscribed, yielding the results to the subscriber.
     */
    fun submitEvents(
        events: List<EventSnapshot>
    ): Publisher<NetworkResult<String>>
}
