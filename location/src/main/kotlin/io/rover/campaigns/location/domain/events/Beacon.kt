package io.rover.campaigns.location.domain.events

import io.rover.campaigns.core.data.domain.Attributes
import io.rover.campaigns.location.domain.Beacon

fun Beacon.asAttributeValue(): Attributes {
    return hashMapOf(
        Pair("id", id.rawValue),
        Pair("name", name),
        Pair("uuid", uuid.toString()),
        Pair("major", major),
        Pair("minor", minor),
        Pair("name", name),
        Pair("tags", tags)
    )
}
