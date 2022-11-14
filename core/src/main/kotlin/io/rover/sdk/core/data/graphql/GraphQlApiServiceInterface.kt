package io.rover.sdk.core.data.graphql

import io.rover.sdk.core.data.GraphQlRequest
import io.rover.sdk.core.data.NetworkResult
import io.rover.sdk.core.data.domain.EventSnapshot
import io.rover.sdk.core.data.domain.Experience
import io.rover.sdk.core.data.graphql.operations.FetchExperienceRequest
import org.reactivestreams.Publisher

interface GraphQlApiServiceInterface {
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

    fun fetchExperience(
            query: FetchExperienceRequest.ExperienceQueryIdentifier
    ): Publisher<NetworkResult<Experience>>
}
