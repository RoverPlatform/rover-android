package io.rover.core.data

/**
 * A version of Authentication Context that works with simply a standard SDK Key, acquired from
 * [Rover Profile/Account settings](https://app.rover.io/settings/overview), as "Server Key" under
 * the "Account Tokens"->"Android" section.
 */
data class ServerKey(
    override val sdkToken: String?
) : AuthenticationContext {
    override val bearerToken: String? = ""
}