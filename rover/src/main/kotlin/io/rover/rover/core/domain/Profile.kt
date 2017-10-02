package io.rover.rover.core.domain

data class Profile(
    val identifier: String?,
    val attributes: Map<String, String>
) {
    companion object
}
