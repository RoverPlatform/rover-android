/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.ticketmaster

import android.os.Bundle

/**
 * An API to set and clear Ticketmaster credentials on Rover after a user signs in with the
 * Ticketmaster [Tickets SDK](https://ignite.ticketmaster.com/docs/tickets-sdk-overview).
 */
interface TicketmasterAuthorizer {
    /**
     * Set the user's Ticketmaster credentials after a successful sign-in with the Ticketmaster Ignite
     * [Tickets SDK](https://ignite.ticketmaster.com/docs/tickets-sdk-overview). Implement the
     * `onMemberUpdated()` method in your `com.ticketmaster.tickets.login.SimpleLoginListener` and
     * call this method passing in values from the `member`.
     *
     * @param id The result of the second parameter's (`member`) call to `getGlobalId()`
     * property.
     *
     * Example:
     *
     * ```kotlin
     * fun setupAnalytics() {
     *   TmxLoginNotifier.getInstance().registerLoginListener(this)
     * }
     *
     * override fun onMemberUpdated(backendName: TMLoginApi.BackendName, member: UserInfoManager.MemberInfo?) {
     *   member?.let {
     *     Rover.shared.ticketmasterAuthorizer.setTicketmasterId(
     *       member.getGlobalId()
     *     )
     *   }
     * }
     * ```
     */
    fun setTicketmasterId(id: String)

    /**
     * Clear the user's Ticketmaster credentials after a successful sign-out with the Ticketmaster
     * Ignite [Tickets SDK](https://ignite.ticketmaster.com/docs/tickets-sdk-overview).
     * Implement the `onLogoutSuccessful()` method in your `com.ticketmaster.tickets.login.SimpleLoginListener`
     * and call this method.
     *
     * Example:
     *
     * ```kotlin
     * fun setupAnalytics() {
     *   TmxLoginNotifier.getInstance().registerLoginListener(this)
     * }
     *
     * override fun onLogoutSuccessful(backendName: TMLoginApi.BackendName) {
     *   Rover.shared.ticketmasterAuthorizer.clearCredentials()
     * }
     * ```
     */
    fun clearCredentials()
}
