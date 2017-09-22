package io.rover.rover.services.network

/**
 * Interface to which Network Request types must conform.
 *
 * @param P Type of parameters.
 * @param O Type of the returned payload.
 */
interface NetworkRequest<P, O> {
    // Network requests have several different concerns.

    // Parameters
    // graphql request type (query/mutation)
    // GraphQL query DSL (multiline string constant).
    // its parameters/data type thereof (DTO)
    // its output/data type thereof (DTO)
    // mapping to domain types (this may be appropriately put elsewhere; decide later)
    // the boilerplate of JSON field mapping (since we don't get to use gson or have any
    // other equivalent to Swift's Encodable/Decodable.

    // TODO: may instead just make this a JSON type
    fun mapParametersPayload(parameters: P): String

    fun mapOutputPayload(output: String): O

    val graphQLQuery: String
}
