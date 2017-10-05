package io.rover.rover.core.domain

data class DeviceState(
    val profile: Profile,
    val regions: Set<Region>
) {
    companion object
}
