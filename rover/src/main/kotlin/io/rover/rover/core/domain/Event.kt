package io.rover.rover.core.domain

import io.rover.rover.platform.DateFormatting
import org.json.JSONObject
import java.util.*

data class Event(
    val attributes: HashMap<String, String>,
    val name: String,
    val timestamp: Date,
    val id: UUID
) {
    companion object
}

//
