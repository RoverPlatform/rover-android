package io.rover.notifications.domain.events

import io.rover.core.data.domain.Attributes
import io.rover.notifications.domain.Notification

fun Notification.asAttributeValue(): Attributes {
    return hashMapOf(
        Pair("id", id),
        Pair("campaignID", campaignId)
    )
}
