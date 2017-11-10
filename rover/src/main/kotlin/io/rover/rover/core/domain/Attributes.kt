package io.rover.rover.core.domain

/**
 * The Rover API data model sometimes includes unstructured data.  See [Attributes].
 */
sealed class AttributeValue {
    class Integer(val value: kotlin.Int) : AttributeValue()
    class String(val value: kotlin.String) : AttributeValue()
    class Double(val value: kotlin.Double) : AttributeValue()
    class Boolean(val value: kotlin.Boolean) : AttributeValue()
    class URL(val value: java.net.URI) : AttributeValue()
    class Hash(val hash: Map<kotlin.String, AttributeValue>) : AttributeValue()
    class Array(val values: List<AttributeValue>) : AttributeValue()
}

/**
 * In certain places the Rover API returns unstructured data and similarly the Rover SDK is
 * expected to submit unstructured data in yet other places.
 *
 * Attributes and [AttributeValue]s are a strongishly typed way to represent such data.
 */
typealias Attributes = Map<kotlin.String, AttributeValue>
