package io.rover.campaigns.core.events.domain

import io.rover.campaigns.core.data.domain.Attributes
import java.util.Date
import java.util.UUID

data class Event(
    val name: String,
    val attributes: Attributes,
    val timestamp: Date,
    val id: UUID
) {
    constructor(
        name: String,
        attributes: Attributes
    ): this(name, attributes, Date(), UUID.randomUUID())
}
