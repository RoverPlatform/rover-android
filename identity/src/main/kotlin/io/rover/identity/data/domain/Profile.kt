package io.rover.identity.data.domain

import io.rover.core.data.domain.Attributes

data class Profile(
    val identifier: String?,
    val attributes: Attributes
) {
    companion object
}

