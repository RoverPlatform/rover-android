package io.rover.core.data.domain

/**
 * The Rover API data model sometimes includes unstructured data.  See [Attributes].
 */
sealed class AttributeValue {
    data class Integer(val value: kotlin.Int) : AttributeValue()
    data class String(val value: kotlin.String) : AttributeValue()
    data class Double(val value: kotlin.Double) : AttributeValue()
    data class Boolean(val value: kotlin.Boolean) : AttributeValue()
    data class URL(val value: java.net.URI) : AttributeValue()
    data class Hash(val hash: Map<kotlin.String, AttributeValue>) : AttributeValue()
    data class Array(val values: List<AttributeValue>) : AttributeValue()
}

/**
 * In certain places the Rover API returns unstructured data and similarly the Rover SDK is
 * expected to submit unstructured data in yet other places.
 *
 * Attributes and [AttributeValue]s are a strongishly typed way to represent such data.
 */
typealias Attributes = Map<kotlin.String, AttributeValue>
