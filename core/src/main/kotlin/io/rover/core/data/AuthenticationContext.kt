package io.rover.core.data

/**
 * Implementers can furnish consumers with a Rover API account SDK authentication token, or a Bearer
 * OAuth token or null if authentication is not available.
 */
interface AuthenticationContext {
    val sdkToken: String?

    val bearerToken: String?

    fun isAvailable(): Boolean {
        return sdkToken != null || bearerToken != null
    }
}
