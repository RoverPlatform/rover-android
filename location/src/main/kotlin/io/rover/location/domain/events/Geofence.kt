package io.rover.location.domain.events

import io.rover.core.data.domain.AttributeValue
import io.rover.location.domain.Geofence

fun Geofence.asAttributeValue(): AttributeValue {
    return AttributeValue.Object(
        Pair("id", AttributeValue.Scalar.String(id.rawValue)),
        Pair(
            "center",
            AttributeValue.Array(
                listOf(
                    AttributeValue.Scalar.Double(center.latitude),
                    AttributeValue.Scalar.Double(center.longitude)
                )
            )
        ),
        Pair("radius", AttributeValue.Scalar.Double(radius)),
        Pair("name", AttributeValue.Scalar.String(name)),
        Pair("tags", AttributeValue.Array(
            tags.map { AttributeValue.Scalar.String(it )}
        ))
    )
}