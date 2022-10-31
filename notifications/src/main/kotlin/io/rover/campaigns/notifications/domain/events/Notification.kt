package io.rover.campaigns.notifications.domain.events

import io.rover.campaigns.core.data.domain.Attributes
import io.rover.campaigns.notifications.domain.Notification

fun Notification.asAttributeValue(): Attributes {
    return hashMapOf(
        Pair("id", id),
        Pair("campaignID", campaignId)
    )
}
