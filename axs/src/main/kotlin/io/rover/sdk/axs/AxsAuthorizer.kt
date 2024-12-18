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

package io.rover.sdk.axs

/**
 * An API to set and clear AXS credentials on Rover.
 */
interface AxsAuthorizer {
    /**
     * Set the user's AXS user ID after a successful sign-in.
     *
     * @param userID The value of the user's AXS User ID.
     */
    @Deprecated("Use setUserId(userId: String?, flashMemberId: String?, flashMobileId: String?) instead, see https://developer.rover.io/docs/data/axs")
    fun setUserId(userID: String)

    /**
     * Set the user's AXS credentials after a successful sign-in. If `userId` is null, then it is treated as a sign out.
     *
     * @param userId The value of the user's AXS User ID.
     * @param flashMemberId The value of the Flash Seats member ID.
     * @param flashMobileId The value of the Flash Seats mobile ID.
     */
    fun setUserId(userId: String?, flashMemberId: String?, flashMobileId: String?)

    /**
     * Clear the user's AXS credentials after a successful sign-out.
     */
    fun clearCredentials()
}
