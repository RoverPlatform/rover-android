package io.rover.sdk.data.domain.events

import io.rover.sdk.data.domain.Attributes
import io.rover.sdk.data.domain.Block
import io.rover.sdk.data.domain.Experience
import io.rover.sdk.data.domain.Row
import io.rover.sdk.data.domain.Screen

fun Experience.asAttributeValue(): Attributes {
    return hashMapOf(
        Pair("id", id.rawValue),
        Pair("tags", tags),
        Pair("keys", keys),
        Pair("name", name)
    )
}

fun Screen.asAttributeValue(): Attributes {
    return hashMapOf(
        Pair("id", id.rawValue),
        Pair("tags", tags),
        Pair("keys", keys),
        Pair("name", name)
    )
}

fun Block.asAttributeValue(): Attributes {
    return hashMapOf(
        Pair("id", id.rawValue),
        Pair("keys", keys),
        Pair("name", name),
        Pair("tags", tags)
    )
}

fun Row.asAttributeValue(): Attributes {
    return hashMapOf(
        Pair("id", this.id.rawValue),
        Pair("name", this.name),
        Pair("keys", keys),
        Pair("tags", tags)
    )
}
