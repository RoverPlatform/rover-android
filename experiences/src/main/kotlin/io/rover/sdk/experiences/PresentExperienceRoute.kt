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

package io.rover.sdk.experiences

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.parseAsQueryParameters
import io.rover.sdk.core.routing.Route
import io.rover.sdk.experiences.platform.asAndroidUri
import java.net.URI

class PresentExperienceRoute(
    private val context: Context,
    private val urlSchemes: List<String>,
    private val associatedDomains: List<String>,

    private val experienceIntent: (Context, Uri) -> Intent
) : Route {
    override fun resolveUri(uri: URI?): Intent? {
        val mainDomain = associatedDomains.firstOrNull()
        if (mainDomain == null) {
            log.e("No associated domains configured for Rover Experiences.")
            return null
        }

        if (uri == null) {
            return null
        }

        // first, continue to support `presentExperience` legacy deep links as they can
        // still appear in old Rover notifications and so forth:
        if (urlSchemes.contains(uri.scheme.lowercase()) && uri.authority == "presentExperience") {
            val queryParameters = uri.query.parseAsQueryParameters()
            val possibleCampaignId = queryParameters["campaignID"]

            // For now, you need to support deep links with both `experienceID` and `id` to enable backwards compatibility
            // For Rover 3.0 devices, the Rover server will send deep links within push notifications using `experienceID` parameter. For backwards compatibility, `id` parameter is also supported.
            val experienceId = queryParameters["experienceID"] ?: queryParameters["id"]

            val possibleScreenId = queryParameters["screenID"]

            if (experienceId == null) {
                log.w("A presentExperience deep link lacked either an `id` parameter.")
                return null
            }

            // construct new URI:
            val newUriBuilder = Uri.Builder()
                .authority(mainDomain)
                .scheme("https")
                .appendPath("v1")
                .appendPath("experiences")
                .appendPath(experienceId)

            possibleCampaignId?.let {
                newUriBuilder.appendQueryParameter("campaignID", it)
            }

            possibleScreenId?.let {
                newUriBuilder.appendQueryParameter("screenID", it)
            }

            val newUri = newUriBuilder.build()
            return experienceIntent(context, newUri)
        }

        // Experiences can be opened either by a deep link or a universal link.
        // the main routing rule: if the URI's host is one of the associated domains, then
        // it is an experience URI and we should open it as an experience (regardless
        // of the scheme, to support either deep or universal/app links.)
        return if (uri.host?.let { associatedDomains.contains(it.lowercase()) } == true) {
            experienceIntent(context, uri.asAndroidUri())
        } else {
            null
        }
    }
}
