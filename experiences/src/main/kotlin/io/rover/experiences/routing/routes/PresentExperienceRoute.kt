package io.rover.experiences.routing.routes

import android.content.Context
import android.content.Intent
import io.rover.core.logging.log
import io.rover.core.platform.parseAsQueryParameters
import io.rover.core.routing.Route
import io.rover.experiences.ui.containers.ExperienceActivity
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
                presentExperienceIntents.displayExperienceIntentFromCampaignLink(uri)
            }
            urlSchemes.contains(uri?.scheme) && uri?.authority == "presentExperience" -> {
                val queryParameters = uri.query.parseAsQueryParameters()
                val possibleCampaignId = queryParameters["campaignID"]
                val possibleExperienceId = queryParameters["id"]
                when {
                    possibleCampaignId != null -> presentExperienceIntents.displayExperienceIntentByCampaignId(possibleCampaignId)
                    possibleExperienceId != null -> presentExperienceIntents.displayExperienceIntentByExperienceId(possibleExperienceId)
                    else -> {
                        log.w("A presentExperience deep link lacked either a `campaignID` or `id` parameter.")
                        null
                    }
                }
            }
            else -> null // no match.
        }
    }
}

/**
 * Override this class to configure Rover to open Experiences with a different Activity other than
 * the bundled [ExperienceActivity].
 */
open class PresentExperienceIntents(
    private val applicationContext: Context
) {
    fun displayExperienceIntentByExperienceId(experienceId: String): Intent {
        return ExperienceActivity.makeIntent(applicationContext, experienceId = experienceId, campaignId = null)
    }

    fun displayExperienceIntentByCampaignId(campaignId: String): Intent {
        return ExperienceActivity.makeIntent(applicationContext, experienceId = null, campaignId = campaignId)
    }

    fun displayExperienceIntentFromCampaignLink(universalLink: URI): Intent {
        return ExperienceActivity.makeIntent(applicationContext, experienceUrl = universalLink.toString())
    }
}