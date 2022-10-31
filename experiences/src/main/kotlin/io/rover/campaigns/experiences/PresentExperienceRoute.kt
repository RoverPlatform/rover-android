package io.rover.campaigns.experiences

import android.content.Context
import android.content.Intent
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.platform.asAndroidUri
import io.rover.campaigns.core.platform.parseAsQueryParameters
import io.rover.campaigns.core.routing.Route
import io.rover.sdk.ui.containers.RoverActivity
import java.net.URI

class PresentExperienceRoute(
    private val urlSchemes: List<String>,
    private val associatedDomains: List<String>,
    private val presentExperienceIntents: PresentExperienceIntents
) : Route {
    override fun resolveUri(uri: URI?): Intent? {
        // Experiences can be opened either by a deep link or a universal link.
        return when {
            (uri?.scheme == "https" || uri?.scheme == "http") && associatedDomains.contains(uri.host) -> {
                // universal link!
                val queryParameters = uri.query?.parseAsQueryParameters()
                val screenID = queryParameters?.let {it["screenID"] }
                presentExperienceIntents.displayExperienceIntentFromCampaignLink(uri, screenID)
            }
            urlSchemes.contains(uri?.scheme) && uri?.authority == "presentExperience" -> {
                val queryParameters = uri.query.parseAsQueryParameters()
                val possibleCampaignId = queryParameters["campaignID"]

                // For now, you need to support deep links with both `experienceID` and `id` to enable backwards compatibility
                // For Rover 3.0 devices, the Rover server will send deep links within push notifications using `experienceID` parameter. For backwards compatibility, `id` parameter is also supported.
                val experienceId = queryParameters["experienceID"] ?: queryParameters["id"]

                val screenID = queryParameters["screenID"]

                if (experienceId == null) {
                    log.w("A presentExperience deep link lacked either a `campaignID` or `id` parameter.")
                    return null
                }

                presentExperienceIntents.displayExperienceIntentByExperienceId(experienceId, possibleCampaignId, screenID)
            }
            else -> null // no match.
        }
    }
}

/**
 * Override this class to configure Rover to open Experiences with a different Activity other than
 * the bundled [RoverActivity].
 */
open class PresentExperienceIntents(
    private val applicationContext: Context
) {
    fun displayExperienceIntentByExperienceId(experienceId: String, possibleCampaignId: String?, screenID: String? = null): Intent {
        return RoverActivity.makeIntent(applicationContext, experienceId = experienceId, campaignId = possibleCampaignId, initialScreenId = screenID)
    }

    fun displayExperienceIntentFromCampaignLink(universalLink: URI, screenID: String? = null): Intent {
        return RoverActivity.makeIntent(applicationContext, experienceUrl = universalLink.asAndroidUri(), initialScreenId = screenID)
    }
}
