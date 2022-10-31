package io.rover.campaigns.core.data.graphql

import io.rover.campaigns.core.data.GraphQlRequest
import io.rover.campaigns.core.data.NetworkResult
import io.rover.campaigns.core.data.domain.EventSnapshot
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
}
