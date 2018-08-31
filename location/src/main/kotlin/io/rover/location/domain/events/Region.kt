package io.rover.location.domain.events

import io.rover.core.data.domain.AttributeValue
import io.rover.location.domain.Region

fun Region.asAttributeValue(): AttributeValue {
    return when(this) {
        is Region.BeaconRegion -> {
            AttributeValue.Object(
                Pair("identifier", AttributeValue.Scalar.String(identifier)),
                Pair("uuid", AttributeValue.Scalar.String(uuid.toString())),
                Pair("major", AttributeValue.Scalar.String(major.toString())),
                Pair("minor", AttributeValue.Scalar.String(minor.toString()))

            )
        }
        is Region.GeofenceRegion -> {
            AttributeValue.Object(
                Pair("identifier", AttributeValue.Scalar.String(identifier)),
                Pair(
                    "center",
                    AttributeValue.Array(
                        listOf(
                            AttributeValue.Scalar.Double(latitude),
                            AttributeValue.Scalar.Double(longitude)
                        )
                    )
                ),
                Pair("radius", AttributeValue.Scalar.Double(radius))
            )
        }
    }
}
