package io.rover.notifications.domain.events

import io.rover.core.data.domain.AttributeValue
import io.rover.notifications.domain.Notification

fun Notification.asAttributeValue(): AttributeValue {
    return AttributeValue.Object(
        Pair("id", AttributeValue.Scalar.String((id))),
        Pair("campaignID", AttributeValue.Scalar.String(campaignId))
    )
}