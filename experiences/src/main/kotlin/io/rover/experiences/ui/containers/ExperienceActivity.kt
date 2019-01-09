package io.rover.experiences.ui.containers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import io.rover.core.R
import io.rover.core.Rover
import io.rover.core.embeddedWebBrowserDisplay
import io.rover.core.logging.log
import io.rover.core.platform.whenNotNull
import io.rover.core.router
import io.rover.core.routing.Router
import io.rover.core.routing.website.EmbeddedWebBrowserDisplayInterface
import io.rover.core.streams.androidLifecycleDispose
import io.rover.core.streams.subscribe
import io.rover.core.ui.concerns.MeasuredBindableView
import io.rover.experiences.ui.ExperienceView
import io.rover.experiences.ui.ExperienceViewModel
import io.rover.experiences.ui.ExperienceViewModelInterface
import io.rover.experiences.ui.navigation.ExperienceExternalNavigationEvent

/**
 * This can display a Rover experience in an Activity, self-contained.
 *
 * You may use this either as a subclass for your own Activity, or as a template for embedding
 * a Rover [ExperienceView] in your own Activities.
 */
open class ExperienceActivity : AppCompatActivity() {
    protected val experienceId: String? by lazy { this.intent.getStringExtra("EXPERIENCE_ID") }

    protected val experienceUrl: String? by lazy { this.intent.getStringExtra("EXPERIENCE_URL") }

    protected val campaignId: String?
        get() = this.intent.getStringExtra("CAMPAIGN_ID")

    /**
     * This method is responsible for performing external navigation events: that is, navigation
     * events emitted by an Experience that "break out" of the Experience's intrinsic navigation
     * flow (ie., moving back and forth amongst Screens).  The default implementation handles
     * exiting the Activity and opening up a web browser.
     *
     * You may override this in a subclass if you want to handle the
     * [ExperienceExternalNavigationEvent.Custom] event you may have emitted in view model subclass,
     * to do some other sort of external behaviour in your app, such as open a native login screen.
     */
    protected open fun dispatchExternalNavigationEvent(externalNavigationEvent: ExperienceExternalNavigationEvent) {
        // TODO: factor this out into a separate object.

        val rover = Rover.shared
        if(rover == null) {
            log.e("ExperienceActivity cannot work unless Rover has been initialized.")
            return
        }

        val router = rover.resolve(Router::class.java)
        if(router == null) {
            log.e("Router not registered in the Rover container.  Ensure that CoreAssembler is passed to Rover.initialize().  Ignoring navigation event.")
            return
        }
        val webDisplay = rover.resolve(EmbeddedWebBrowserDisplayInterface::class.java)
        if(webDisplay == null) {
            log.e("EmbeddedWebBrowserDisplayInterface not registered in the Rover container.  Ensure that CoreAssembler is passed to Rover.initialize(). Ignoring navigation event.")
            return
        }

        when (externalNavigationEvent) {
            is ExperienceExternalNavigationEvent.Exit -> {
                finish()
            }
            is ExperienceExternalNavigationEvent.OpenUri -> {
                router.route(externalNavigationEvent.uri, false).whenNotNull { intent ->
                    ContextCompat.startActivity(
                        this,
                        intent,
                        null
                    )
                }
            }
            is ExperienceExternalNavigationEvent.PresentWebsite -> {
                webDisplay.intentForViewingWebsiteViaEmbeddedBrowser(
                    externalNavigationEvent.url.toString()
                ).whenNotNull { intent ->
                    ContextCompat.startActivity(
                        this,
                        intent,
                        null
                    )
                }
            }
            is ExperienceExternalNavigationEvent.Custom -> {
                log.w("You have emitted a Custom event: $externalNavigationEvent, but did not handle it in your subclass implementation of ExperienceActivity.dispatchExternalNavigationEvent()")
            }
        }
    }

    protected open val experiencesView by lazy { ExperienceView(this) }

    private var experienceViewModel: ExperienceViewModelInterface? = null
        set(viewModel) {
            field = viewModel

            experiencesView.viewModelBinding = viewModel.whenNotNull { MeasuredBindableView.Binding(it) }

            viewModel
                ?.events
                ?.androidLifecycleDispose(this)
                ?.subscribe(
                    { event ->
                        when (event) {
                            is ExperienceViewModelInterface.Event.NavigateTo -> {
                                log.v("Received an external navigation event: ${event.externalNavigationEvent}")
                                dispatchExternalNavigationEvent(event.externalNavigationEvent)
                            }
                        }
                    }, { error ->
                        throw(error)
                    }
                )
        }

    override fun onBackPressed() {
        if (experienceViewModel != null) {
            experienceViewModel?.pressBack()
        } else {
            // default to standard Android back-button behaviour (ie., pop the activity) if our view
            // model isn't yet available.
            super.onBackPressed()
        }
    }

    // The View needs to know about the Activity-level window and several other
    // Activity/Fragment context things in order to temporarily change the backlight.
    private val toolbarHost by lazy { ActivityToolbarHost(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rover = Rover.shared
        if(rover == null) {
            log.w("ExperienceActivity cannot work unless Rover has been initialized.")
            finish()
            return
        }

        // TODO: perhaps this arrangement is not necessary: confirm for sure that it will not pick
        // up the default theme set on the App (although we can hopefully check that they have an
        // actionbar-free theme enabled).

        val displayNoCustomThemeWarningMessage = this.theme.obtainStyledAttributes(
            intArrayOf(R.attr.displayNoCustomThemeWarningMessage)
        ).getBoolean(0, false)

        if (displayNoCustomThemeWarningMessage) {
            log.w("You have set no theme for ExperienceActivity (or your optional subclass thereof) in your AndroidManifest.xml.\n" +
                "In particular, this means the toolbar will not pick up your brand colours.")
        }

        setContentView(
            experiencesView
        )

        // wire up the toolbar host to the ExperienceView.  Note that the activity will be leaked
        // unless you remember to set it back to null in onDestroy().
        experiencesView.toolbarHost = toolbarHost

        // obtain any possibly saved state for the experience view model.  See
        // onSaveInstanceState.
        val state: Parcelable? = savedInstanceState?.getParcelable("experienceState")
        experienceViewModel = when {
            experienceId != null && campaignId == null -> experienceViewModel(
                rover,
                ExperienceViewModel.ExperienceRequest.ById(experienceId!!),
                // obtain any possibly saved state for the experience view model.  See
                // onSaveInstanceState.
                state
            )

            experienceId == null && campaignId != null -> experienceViewModel(
                rover,
                ExperienceViewModel.ExperienceRequest.ByCampaignId(campaignId!!),
                state
            )

            experienceUrl != null -> experienceViewModel(
                rover,
                ExperienceViewModel.ExperienceRequest.ByCampaignUrl(experienceUrl!!),
                state
            )
            else -> throw RuntimeException(
                "Please pass either one of CAMPAIGN_ID/EXPERIENCE_ID or EXPERIENCE_URL. Consider using ExperienceActivity.makeIntent()"
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO: this is liable to be forgotten by implementers making their own activity. If
        // forgotten, the activity will be leaked.
        experiencesView.toolbarHost = null
    }

    private fun experienceViewModel(rover: Rover, experienceRequest: ExperienceViewModel.ExperienceRequest, icicle: Parcelable?): ExperienceViewModelInterface {
            return rover.resolve(ExperienceViewModelInterface::class.java, null, experienceRequest, this.lifecycle, icicle)
                ?: throw RuntimeException("Factory for ExperienceViewModelInterface not registered in Rover DI container.")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        // ExperienceView owns the toolbar, this is so ExperienceView can take your menu and include
        // it in its internal toolbar.
        toolbarHost.menu = menu
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // grab the state for the Experience view out of its view model and store it in the
        // activity's own bundle.
        outState.putParcelable("experienceState", experienceViewModel?.state)
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun makeIntent(packageContext: Context, experienceId: String?, campaignId: String?, activityClass: Class<out Activity> = ExperienceActivity::class.java): Intent {
            return Intent(packageContext, activityClass).apply {
                putExtra("EXPERIENCE_ID", experienceId)
                putExtra("CAMPAIGN_ID", campaignId)
            }
        }

        @JvmStatic
        @JvmOverloads
        fun makeIntent(packageContext: Context, experienceUrl: String, activityClass: Class<out Activity> = ExperienceActivity::class.java): Intent {
            return Intent(packageContext, activityClass).apply {
                putExtra("EXPERIENCE_URL", experienceUrl)
            }
        }
    }
}
