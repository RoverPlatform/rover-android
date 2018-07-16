package io.rover.experiences.routing

//import android.content.Context
//import android.content.Intent
//import io.rover.experiences.ui.containers.StandaloneExperienceHostActivity
//import io.rover.core.routing.DefaultTopLevelNavigation
//import io.rover.core.routing.TopLevelNavigation
//import java.net.URI

//open class ExperienceEnabledTopLevelNavigation(
//    private val applicationContext: Context,
//    private val prior: TopLevelNavigation
//): TopLevelNavigation by prior {
//    override fun displayExperienceIntentByExperienceId(experienceId: String): Intent {
//        return StandaloneExperienceHostActivity.makeIntent(applicationContext, experienceId)
//    }
//
//    override fun displayExperienceIntentByCampaignId(campaignId: String): Intent {
//        return StandaloneExperienceHostActivity.makeIntent(applicationContext, null, campaignId)
//    }
//
//    override fun displayExperienceIntentFromCampaignLink(universalLink: URI): Intent {
//        return StandaloneExperienceHostActivity.makeIntent(applicationContext, universalLink.toString())
//    }
//}
