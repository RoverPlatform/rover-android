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
 * Returns the most specific available user identifier from the current user info, following
 * the standard Rover priority order: direct userID, Ticketmaster ID, SeatGeek client ID,
 * SeatGeek CRM ID. Returns null if none are present or non-blank.
 *
 * Device identifier is intentionally excluded — callers that need a device-level fallback
 * should handle that themselves.
 */
fun UserInfoInterface.resolveUserIdentifier(): String? {
    val info = currentUserInfo

    (info["userID"] as? String)?.takeIf { it.isNotBlank() }?.let { return it }

    val tmMap = info["ticketmaster"] as? Map<*, *>
    (tmMap?.get("ticketmasterID") as? String)?.takeIf { it.isNotBlank() }?.let { return it }

    val sgMap = info["seatGeek"] as? Map<*, *>
    (sgMap?.get("seatGeekClientID") as? String)?.takeIf { it.isNotBlank() }?.let { return it }
    (sgMap?.get("seatGeekID") as? String)?.takeIf { it.isNotBlank() }?.let { return it }

    return null
}
