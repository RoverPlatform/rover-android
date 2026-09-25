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

import io.rover.sdk.core.data.domain.Attributes
import io.rover.sdk.core.privacy.PrivacyService

/**
 * The user info keys that the SDK itself gives meaning to.
 *
 * The ticketing integration modules write these keys, but they are not the only writers: some
 * integrators skip the modules entirely and write the same keys straight into user info
 * themselves. Core therefore owns the names.
 */
object UserInfoKeys {
    const val USER_ID = "userID"

    const val TICKETMASTER = "ticketmaster"
    const val TICKETMASTER_ID = "ticketmasterID"

    const val SEAT_GEEK = "seatGeek"
    const val SEAT_GEEK_CLIENT_ID = "seatGeekClientID"

    // NOTE: seatGeekID is actually the CRM ID. The name is kept for backwards compatibility.
    const val SEAT_GEEK_ID = "seatGeekID"

    const val AXS = "axs"

    // Legacy flat AXS keys, written by older versions of the AXS module.
    const val FLASH_MEMBER_ID = "flashMemberID"
    const val FLASH_MOBILE_ID = "flashMobileID"

    const val ECID = "ecid"

    /**
     * Top-level user info keys that identify the fan to a ticketing or marketing platform.
     *
     * While tracking mode is anything other than [PrivacyService.TrackingMode.Default] these are
     * withheld from everything that leaves the SDK. They are never removed from the stored user
     * info: withholding is recoverable, erasing is not.
     */
    val privacySensitiveKeys: Set<String> = setOf(
        TICKETMASTER,
        SEAT_GEEK,
        AXS,
        FLASH_MEMBER_ID,
        FLASH_MOBILE_ID,
        ECID
    )
}

/**
 * The view of [storedUserInfo] that the SDK is allowed to report under the given [trackingMode].
 *
 * This is a pure function of its two arguments, holding no state of its own, so it is safe to
 * apply repeatedly and in any order.
 */
fun filterUserInfoForPrivacy(
    storedUserInfo: Attributes,
    trackingMode: PrivacyService.TrackingMode
): Attributes = when (trackingMode) {
    PrivacyService.TrackingMode.Default -> storedUserInfo
    PrivacyService.TrackingMode.Anonymized -> storedUserInfo.filterKeys {
        it !in UserInfoKeys.privacySensitiveKeys
    }
}
