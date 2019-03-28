package io.rover.experiences.ui.containers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import io.rover.core.EmbeddedWebBrowserDisplay
import io.rover.core.EmbeddedWebBrowserDisplayInterface
import io.rover.core.R
import io.rover.core.Rover
import io.rover.core.embeddedWebBrowserDisplay
import io.rover.core.logging.log
import io.rover.core.platform.asAndroidUri
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
    protected open val experienceId: String? by lazy { this.intent.getStringExtra("EXPERIENCE_ID") }

    protected open val experienceUrl: String? by lazy { this.intent.getStringExtra("EXPERIENCE_URL") }

    protected val campaignId: String?
        get() = this.intent.getStringExtra("CAMPAIGN_ID")

    /***
     * Open a URI directly (not to be confused with a so-called Custom Tab, which is a web browser
     * embedded within the app).
     */
    protected open fun openUri(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        this.startActivity(intent)
    }

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
        when (externalNavigationEvent) {
            is ExperienceExternalNavigationEvent.Exit -> {
                finish()
            }
            is ExperienceExternalNavigationEvent.OpenUri -> {
                openUri(externalNavigationEvent.uri.asAndroidUri())
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

    /**
     * [ExperienceViewModel] is responsible for describing the appearance and behaviour of the
     * experience contents.  If you customize it, you must return your customized version here.
     */
    protected open val experiencesView by lazy { ExperienceView(this) }

    /**
     * Holds the currently set view model, including side-effect behaviour for binding it to the
     * Experiences View.
     *
     * This is a mutable property because it cannot be set up at Activity construction time in the
     * constructor; at that stage the requesting Intent and its parameters are not available.
     */
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

    /**
     * Provides a method for opening URIs with a Custom Chrome Tab.
     */
    // TODO: go to new simple container, don't put it here like I did on iOS.
    protected open val webDisplay: EmbeddedWebBrowserDisplay by lazy {
        EmbeddedWebBrowserDisplay()
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
        experiencesView.toolbarHost = null
    }

    /**
     * This method If you customize it, you must return your customized version here.
     */
    protected open fun experienceViewModel(rover: Rover, experienceRequest: ExperienceViewModel.ExperienceRequest, icicle: Parcelable?): ExperienceViewModelInterface {
            // TODO: go to new simple container.
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
