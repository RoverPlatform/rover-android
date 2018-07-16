package io.rover.core.routing

import android.content.Intent
import io.rover.core.container.Assembler
import java.net.URI


interface Router {
    /**
     * Map the given [uri] to an Intent as per the relevant registered [Route].  If nothing in the
     * currently installed Rover SDK modules can provide an Intent for the URI.
     *
     * @param inbound If true, means the link is being executed by something outside of the Rover
     * SDK, that is, an intent arriving from Android.  If false, means the link is being emitted by
     * something within the Rover SDK, and that any URIs that fail to match any registered routes
     * should be deferred to Android itself.
     */
    fun route(uri: URI?, inbound: Boolean): Intent

    /**
     * Register the given route.  Should typically be called in [Assembler.afterAssembly]
     * implementations.
     */
    fun registerRoute(route: Route)
}

interface Route {
    /**
     * Return an [Intent] for the given URI if this route is capable of handling it.
     *
     * Note; these do not check the Schema.
     */
    fun resolveUri(uri: URI?): Intent?
}

///**
// * Responsible for generating Intents that will route the user to the appropriate place in your app
// * for viewing Rover-mediated content.
// *
// * A default implementation is provided by [DefaultTopLevelNavigation], which uses the standalone
// * activities bundled along with the Rover SDK.  However, you will need your own implementation of
// * [TopLevelNavigation] in your application if you wish to host either the ExperienceView or
// * NavigationCenterView directly in your own Activities.
// */
//interface TopLevelNavigation {
//    /**
//     * Generate an Intent for displaying an Experience from an explicit experience id.
//     */
//    fun displayExperienceIntentByExperienceId(experienceId: String): Intent
//
//    fun displayExperienceIntentByCampaignId(campaignId: String): Intent
//
//    /**
//     * Generate an Intent for displaying an Experience from an opaque experience universal link.
//     */
//    fun displayExperienceIntentFromCampaignLink(universalLink: URI): Intent
//
//    /**
//     * Generate an Intent for navigating your app to the Notification Center.
//     *
//     * For example, if you host the Notification Center within the Settings area of your app, and
//     * your app is a single-Activity app or otherwise using some sort of custom routing arrangement
//     * (such as Fragments or Conductor), then you will need to make the Intent address the
//     * appropriate activity, and command it with arguments to navigate to the appropriate place.
//     */
//    fun displayNotificationCenterIntent(): Intent
//
//    fun openAppIntent(): Intent
//}

interface LinkOpenInterface {
    /**
     * Map a URI just received for a deep/universal link to an explicit, mapped intent.
     */
    fun localIntentForReceived(receivedUri: URI): List<Intent>
}
