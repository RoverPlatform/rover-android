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

package io.rover.sdk.core.events

/**
 * Returns the most specific available user identifier from the reported user info, following
 * the standard Rover priority order: direct userID, Ticketmaster ID, SeatGeek client ID,
 * SeatGeek CRM ID. Returns null if none are present or non-blank.
 *
 * This reads [UserInfoInterface.reportedUserInfo] rather than the stored value, so while
 * tracking is anonymized the ticketing identities are withheld from Engage and App Screens
 * requests even though they remain on the device.
 *
 * Device identifier is intentionally excluded — callers that need a device-level fallback
 * should handle that themselves.
 */
fun UserInfoInterface.resolveUserIdentifier(): String? {
    val info = reportedUserInfo

    (info[UserInfoKeys.USER_ID] as? String)?.takeIf { it.isNotBlank() }?.let { return it }

    val tmMap = info[UserInfoKeys.TICKETMASTER] as? Map<*, *>
    (tmMap?.get(UserInfoKeys.TICKETMASTER_ID) as? String)?.takeIf { it.isNotBlank() }?.let { return it }

    val sgMap = info[UserInfoKeys.SEAT_GEEK] as? Map<*, *>
    (sgMap?.get(UserInfoKeys.SEAT_GEEK_CLIENT_ID) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
    (sgMap?.get(UserInfoKeys.SEAT_GEEK_ID) as? String)?.takeIf { it.isNotBlank() }?.let { return it }

    return null
}
