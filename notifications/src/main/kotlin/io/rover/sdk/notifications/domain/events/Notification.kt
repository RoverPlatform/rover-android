package io.rover.sdk.notifications.domain.events

import io.rover.sdk.core.data.domain.Attributes
import io.rover.sdk.notifications.domain.Notification

fun Notification.asAttributeValue(): Attributes {
    return hashMapOf(
        Pair("id", id),
        Pair("campaignID", campaignId)
    )
}
