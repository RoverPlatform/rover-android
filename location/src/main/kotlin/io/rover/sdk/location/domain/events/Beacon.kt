package io.rover.sdk.location.domain.events

import io.rover.sdk.core.data.domain.Attributes
import io.rover.sdk.location.domain.Beacon

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
