package io.rover.location.domain

import io.rover.core.data.domain.ID
import java.util.UUID

data class Beacon(
    var id: ID,
    val name: String,
    val uuid: UUID,
    val major: Int,
    val minor: Int,
    val tags: List<String>
) {
    companion object

    val identifier: String = "$uuid:$major:$minor"
}
