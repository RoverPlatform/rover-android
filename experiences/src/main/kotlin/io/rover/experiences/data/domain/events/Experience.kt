package io.rover.experiences.data.domain.events

import io.rover.core.data.domain.AttributeValue
import io.rover.experiences.data.domain.Block
import io.rover.experiences.data.domain.Experience
import io.rover.experiences.data.domain.Row
import io.rover.experiences.data.domain.Screen

fun Experience.asAttributeValue(): AttributeValue {
    return AttributeValue.Object(
        hashMapOf(
            Pair("id", AttributeValue.Scalar.String(id.rawValue)),
            Pair("tags", AttributeValue.Array(tags.map { AttributeValue.Scalar.String(it) })),
            Pair("keys", AttributeValue.Object(keys.mapValues { (_, value) ->
                AttributeValue.Scalar.String(value)
            })),
            Pair("name", AttributeValue.Scalar.String(name))
        ) + if (campaignId != null) { hashMapOf(Pair("campaignID", AttributeValue.Scalar.String(campaignId))) } else hashMapOf()
    )
}

fun Screen.asAttributeValue(): AttributeValue {
    return AttributeValue.Object(
        Pair("id", AttributeValue.Scalar.String(id.rawValue)),
        Pair("tags", AttributeValue.Array(tags.map { AttributeValue.Scalar.String(it) })),
        Pair("keys", AttributeValue.Object(keys.mapValues { (_, value) ->
            AttributeValue.Scalar.String(value)
        })),
        Pair("name", AttributeValue.Scalar.String(name))
    )
}

fun Block.TapBehavior.asAttributeValue(): AttributeValue {
    return when(this) {
        is Block.TapBehavior.GoToScreen -> AttributeValue.Object(
            Pair("type", AttributeValue.Scalar.String("goToScreen")),
            Pair("screenID", AttributeValue.Scalar.String(screenId.rawValue))
        )

        is Block.TapBehavior.None ->AttributeValue.Object(
            Pair("type", AttributeValue.Scalar.String("none"))
        )
        is Block.TapBehavior.OpenUri -> AttributeValue.Object(
            // notice URI vs URL.  It's named URL in the event.
            Pair("type", AttributeValue.Scalar.String("openURL")),
            Pair("url", AttributeValue.Scalar.String(uri.toString()))
        )
        is Block.TapBehavior.PresentWebsite -> AttributeValue.Object(
            Pair("type", AttributeValue.Scalar.String("presentWebsite")),
            Pair("url", AttributeValue.Scalar.String(url.toString()))
        )
    }
}

fun Block.asAttributeValue(): AttributeValue {
    return AttributeValue.Object(
        Pair("id", AttributeValue.Scalar.String(id.rawValue)),
        Pair("tapBehavior", tapBehavior.asAttributeValue()),
        Pair("keys", AttributeValue.Object(keys.mapValues { (_, value) ->
            AttributeValue.Scalar.String(value)
        })),
        Pair("name", AttributeValue.Scalar.String(name)),
        Pair("tags", AttributeValue.Array(tags.map { AttributeValue.Scalar.String(it) }))
    )
}

fun Row.asAttributeValue(): AttributeValue {
    return AttributeValue.Object(
        Pair("id", AttributeValue.Scalar.String(this.id.rawValue)),
        Pair("name", AttributeValue.Scalar.String(this.name)),
        Pair("keys", AttributeValue.Object(keys.mapValues { (_, value) ->
            AttributeValue.Scalar.String(value)
        })),
        Pair("tags", AttributeValue.Array(tags.map { AttributeValue.Scalar.String(it) }))
    )
}