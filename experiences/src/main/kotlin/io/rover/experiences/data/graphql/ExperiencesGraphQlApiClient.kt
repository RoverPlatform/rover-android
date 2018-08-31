package io.rover.experiences.data.graphql

import io.rover.core.data.NetworkResult
import io.rover.core.data.graphql.GraphQlApiServiceInterface
import io.rover.experiences.data.graphql.operations.FetchExperienceRequest
import io.rover.experiences.data.domain.Experience
import org.reactivestreams.Publisher

open class ExperiencesGraphqlApiClient(
    private val graphQlApiService: GraphQlApiServiceInterface
) {
    /**
     * Retrieves the experience when subscribed and yields it to the subscriber.
     */
    open fun fetchExperience(
        query: FetchExperienceRequest.ExperienceQueryIdentifier
    ): Publisher<NetworkResult<Experience>> {
        return graphQlApiService.operation(
            FetchExperienceRequest(query)
        )
    }
}
