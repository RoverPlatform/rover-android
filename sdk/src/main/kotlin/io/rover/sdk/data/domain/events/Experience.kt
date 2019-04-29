package io.rover.sdk.data.domain.events

import io.rover.sdk.data.domain.Attributes
import io.rover.sdk.data.domain.Block
import io.rover.sdk.data.domain.Experience
import io.rover.sdk.data.domain.Row
import io.rover.sdk.data.domain.Screen

fun Experience.asAttributeValue(campaignId: String?): Attributes {
    return hashMapOf(
        Pair("experienceID", id.rawValue),
        Pair("experienceName", name)
    ) + if (campaignId != null) { hashMapOf(Pair("campaignID", campaignId)) } else hashMapOf()
}

fun Screen.asAttributeValue(): Attributes {
    return hashMapOf(
        Pair("screenID", id.rawValue),
        Pair("screenName", name)
    )
}

fun Block.asAttributeValue(): Attributes {
    return hashMapOf(
        Pair("blockID", id.rawValue),
        Pair("blockName", name)
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
