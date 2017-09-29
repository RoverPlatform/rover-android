package io.rover.rover.core.domain

data class ApplicationState(
    val profile: Profile,
    val regions: Set<Region>
) {
    companion object
}
