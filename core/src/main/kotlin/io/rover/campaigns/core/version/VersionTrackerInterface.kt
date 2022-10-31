package io.rover.campaigns.core.version

/**
 * Statefully tracks the version number of the app locally and emits Rover eventForSessionBoundary whenever it
 * changes.
 */
interface VersionTrackerInterface {
    /**
     * Emit Rover Events if an upgrade/install/downgrade has taken place.
     */
    fun trackAppVersion()
}