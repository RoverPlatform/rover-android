package io.rover.experiences.routing.routes

import android.content.Context
import android.content.Intent
import io.rover.core.logging.log
import io.rover.core.platform.parseAsQueryParameters
import io.rover.core.routing.Route
import io.rover.experiences.ui.containers.StandaloneExperienceHostActivity
import java.net.URI

class PresentExperienceRoute(
    private val deepLinkScheme: String,
    private val presentExperienceIntents: PresentExperienceIntents
): Route {
    override fun resolveUri(uri: URI?): Intent? {
        log.v("EVALUATING URI $uri, scheme is ${uri?.scheme} and authority is ${uri?.authority}, scheme to match is $deepLinkScheme")
        // Experiences can be opened either by a deep link or a universal link.
        return when {
            uri?.scheme == "https" || uri?.scheme == "http" -> {
                // universal link!
                presentExperienceIntents.displayExperienceIntentFromCampaignLink(uri)
            }
            uri?.scheme == deepLinkScheme && uri.authority == "presentExperience" -> {
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
 * the bundled [StandaloneExperienceHostActivity].
 */
open class PresentExperienceIntents(
    private val applicationContext: Context
) {
    fun displayExperienceIntentByExperienceId(experienceId: String): Intent {
        return StandaloneExperienceHostActivity.makeIntent(applicationContext, experienceId)
    }

    fun displayExperienceIntentByCampaignId(campaignId: String): Intent {
        return StandaloneExperienceHostActivity.makeIntent(applicationContext, null, campaignId)
    }

    fun displayExperienceIntentFromCampaignLink(universalLink: URI): Intent {
        return StandaloneExperienceHostActivity.makeIntent(applicationContext, universalLink.toString())
    }
}