package io.rover.rover.core.domain

data class Device(
    val profile: Profile,
    val regions: Set<Region>
) {
    companion object
}
