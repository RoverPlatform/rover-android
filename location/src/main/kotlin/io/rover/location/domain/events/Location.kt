package io.rover.location.domain.events

import io.rover.core.data.domain.AttributeValue
import io.rover.location.domain.Location

fun Location.asAttributeValue(): AttributeValue {
    return AttributeValue.Object(
        hashMapOf(
            Pair(
                "coordinate",
                AttributeValue.Array(
                    listOf(
                        AttributeValue.Scalar.Double(latitude),
                        AttributeValue.Scalar.Double(longitude)
                    )
                )
            ),
            Pair("altitude", AttributeValue.Scalar.Double(altitude))
        ) + (if (horizontalAccurancy != null) {
            hashMapOf(Pair("horizontalAccuracy", AttributeValue.Scalar.Double(horizontalAccurancy.toDouble())))
        } else hashMapOf()) + if (verticalAccuracy != null) {
            hashMapOf(Pair("verticalAccuracy", AttributeValue.Scalar.Double(verticalAccuracy.toDouble())))
        } else hashMapOf()
    )
}
