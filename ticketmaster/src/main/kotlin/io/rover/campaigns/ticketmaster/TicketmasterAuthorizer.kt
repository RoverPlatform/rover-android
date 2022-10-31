package io.rover.campaigns.ticketmaster

/**
 * An API to set and clear Ticketmaster credentials on Rover after a user signs in with the
 * Ticketmaster [Presence
 * SDK](https://developer.ticketmaster.com/products-and-docs/sdks/presence-sdk/).
 */
interface TicketmasterAuthorizer {
    /**
     * Set the user's Ticketmaster credentials after a successful sign-in with the [Presence
     * SDK](https://developer.ticketmaster.com/products-and-docs/sdks/presence-sdk/). Implement the
     * `onMemberUpdated()` method in your `TMLoginListener` and call this
     * method passing in values from the `PresenceMember`.

     * @param backendNameOrdinal Look at the first parameter, `backendName`, and use ordinal() to
     * get the enum as an integer (0 if HOST, or 1 if ARCHTICS).
     *
     * @param hostMemberID The value of the second parameter's (`PresenceMember`) `hostMemberId`
     * property.  If given as null, then no Ticketmaster sync will occur.
     *
     * Example:
     *
     * ```kotlin
     * override fun onMemberUpdated(backendName: TMLoginApi.BackendName, memberInfo: TMLoginApi.MemberInfo?) {
     *     RoverCampaigns.shared.resolve(TicketmasterAuthorizer::class.java)?.setCredentials(
     *         backendName.ordinal,
     *         memberInfo?.memberId
     *     )
     * }
     * ```
    */
    @Deprecated("Use setCredentials(id: String, email: String?, firstName: String?) instead.")
    fun setCredentials(backendNameOrdinal: Int, memberId: String?)

    /**
     * Set the user's Ticketmaster credentials after a successful sign-in with the [Presence
     * SDK](https://developer.ticketmaster.com/products-and-docs/sdks/presence-sdk/). Implement the
     * `onMemberUpdated()` method in your `TMLoginListener` and call this
     * method passing in values from the `memberInfo`. Only call this function if the value returned
     * from (`memberInfo?.memberId`) is not null.

     * @param id The value of the second parameter's (`memberInfo`) `id`
     * property.
     *
     * @param email The value of the second parameter's (`memberInfo`) `email`
     * property.
     *
     * @param firstName The value of the second parameter's (`memberInfo`) `firstName`
     * property.
     *
     * Example:
     *
     * ```kotlin
     * override fun onMemberUpdated(backendName: TMLoginApi.BackendName, memberInfo: TMLoginApi.MemberInfo?) {
     *     memberInfo?.let {
     *           RoverCampaigns.shared.resolve(TicketmasterAuthorizer::class.java)?.setCredentials(
     *               it.memberId,
     *               it.email,
     *               it..firstName
     *           )
     *      }
     * }
     * ```
     */
    fun setCredentials(id: String, email: String? = null, firstName: String? = null)

    /**
     * Clear the user's Ticketmaster credentials after a successful sign-out with the [Presence
     * SDK](https://developer.ticketmaster.com/products-and-docs/sdks/presence-sdk/). Implement the
     * `onLogoutAllSuccessful()` method in your `TMLoginListener` and call this method.
     */
    fun clearCredentials()
}
