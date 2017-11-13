package io.rover.rover.core.domain

import java.util.*

data class Event(
    val attributes: Attributes,
    val name: String,
    val timestamp: Date,
    val id: UUID
) {
    companion object
}

//
