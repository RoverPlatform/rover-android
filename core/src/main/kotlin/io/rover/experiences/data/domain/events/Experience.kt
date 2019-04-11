package io.rover.experiences.data.domain.events

import io.rover.core.data.domain.Attributes
import io.rover.experiences.data.domain.Block
import io.rover.experiences.data.domain.Experience
import io.rover.experiences.data.domain.Row
import io.rover.experiences.data.domain.Screen

fun Experience.asAttributeValue(): Attributes {
    return hashMapOf(
        Pair("id", id.rawValue),
        Pair("tags", tags),
        Pair("keys", keys),
        Pair("name", name)
    ) + if (campaignId != null) { hashMapOf(Pair("campaignID", campaignId)) } else hashMapOf()
}

fun Screen.asAttributeValue(): Attributes {
    return hashMapOf(
        Pair("id", id.rawValue),
        Pair("tags", tags),
        Pair("keys", keys),
        Pair("name", name)
    )
}

fun Block.TapBehavior.asAttributes(): Attributes {
    return when(this) {
        is Block.TapBehavior.GoToScreen -> hashMapOf(
            Pair("type", "goToScreen"),
            Pair("screenID", screenId.rawValue)
        )

        is Block.TapBehavior.None -> hashMapOf(
            Pair("type", "none")
        )
        is Block.TapBehavior.OpenUri -> hashMapOf(
            // notice URI vs URL.  It's named URL in the event.
            Pair("type", "openURL"),
            Pair("url", uri.toString())
        )
        is Block.TapBehavior.PresentWebsite -> hashMapOf(
            Pair("type", "presentWebsite"),
            Pair("url", url.toString())
        )
    }
}

fun Block.asAttributeValue(): Attributes {
    return hashMapOf(
        Pair("id", id.rawValue),
        Pair("tapBehavior", tapBehavior.asAttributes()),
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
