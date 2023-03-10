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
     * method passing in values from the `memberInfo`. Only call this function if the value returned
     * from (`memberInfo?.memberId`) is not null.

     * @param id The value of the second parameter's (`memberInfo`) `id`
     * property.
     *
     * Example:
     *
     * ```kotlin
     * override fun onMemberUpdated(backendName: TMLoginApi.BackendName, memberInfo: TMLoginApi.MemberInfo?) {
     *     memberInfo?.let {
     *           Rover.shared.ticketmasterAuthorizer.setTicketmasterId(
     *               it.memberId
     *           )
     *      }
     * }
     * ```
     */
    fun setTicketmasterId(id: String)

    /**
     * Clear the user's Ticketmaster credentials after a successful sign-out with the [Presence
     * SDK](https://developer.ticketmaster.com/products-and-docs/sdks/presence-sdk/). Implement the
     * `onLogoutAllSuccessful()` method in your `TMLoginListener` and call this method.
     */
    fun clearCredentials()
}
