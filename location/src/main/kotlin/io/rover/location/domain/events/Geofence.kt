package io.rover.location.domain.events

import io.rover.campaigns.core.data.domain.Attributes
import io.rover.location.domain.Geofence

fun Geofence.asAttributeValue(): Attributes {
    return hashMapOf(
        Pair("id", id.rawValue),
        Pair(
            "center",
            listOf(
                center.latitude,
                center.longitude
            )
        ),
        Pair("radius", radius),
        Pair("name", name),
        Pair(
            "tags",
            tags
        )
    )
}