package io.rover.sdk.ui.containers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import io.rover.sdk.services.EmbeddedWebBrowserDisplay
import io.rover.sdk.R
import io.rover.sdk.Rover
import io.rover.sdk.logging.log
import io.rover.sdk.platform.asAndroidUri
import io.rover.sdk.platform.whenNotNull
import io.rover.sdk.streams.androidLifecycleDispose
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.RoverView
import io.rover.sdk.ui.RoverViewModel
import io.rover.sdk.ui.RoverViewModelInterface
import io.rover.sdk.ui.navigation.ExperienceExternalNavigationEvent

/**
 * This can display a Rover experience in an Activity, self-contained.
 *
 * You may use this either as a subclass for your own Activity, or as a template for embedding
 * a Rover [RoverView] in your own Activities.
 */
open class RoverActivity : AppCompatActivity() {
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
                webDisplay?.intentForViewingWebsiteViaEmbeddedBrowser(
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
                log.w("You have emitted a Custom event: $externalNavigationEvent, but did not handle it in your subclass implementation of RoverActivity.dispatchExternalNavigationEvent()")
            }
        }
    }

    /**
     * [RoverViewModel] is responsible for describing the appearance and behaviour of the
     * experience contents.  If you customize it, you must return your customized version here.
     */
    protected open val experiencesView by lazy { RoverView(this) }

    /**
     * Holds the currently set view model, including side-effect behaviour for binding it to the
     * Experiences View.
     *
     * This is a mutable property because it cannot be set up at Activity construction time in the
     * constructor; at that stage the requesting Intent and its parameters are not available.
     */
    private var roverViewModel: RoverViewModelInterface? = null
        set(viewModel) {
            field = viewModel

            experiencesView.viewModelBinding = viewModel.whenNotNull { MeasuredBindableView.Binding(it) }

            viewModel
                ?.events
                ?.androidLifecycleDispose(this)
                ?.subscribe(
                    { event ->
                        when (event) {
                            is RoverViewModelInterface.Event.NavigateTo -> {
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
    protected open val webDisplay: EmbeddedWebBrowserDisplay? by lazy {
        val rover = Rover.shared
        if(rover == null) {
            log.w("RoverActivity cannot work unless Rover has been initialized.")
            finish()
            null
        } else {
            rover.webBrowserDisplay
        }
    }

    override fun onBackPressed() {
        if (roverViewModel != null) {
            roverViewModel?.pressBack()
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
            log.w("RoverActivity cannot work unless Rover has been initialized.")
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
            log.w("You have set no theme for RoverActivity (or your optional subclass thereof) in your AndroidManifest.xml.\n" +
                "In particular, this means the toolbar will not pick up your brand colours.")
        }

        setContentView(
            experiencesView
        )

        // wire up the toolbar host to the RoverView.  Note that the activity will be leaked
        // unless you remember to set it back to null in onDestroy().
        experiencesView.toolbarHost = toolbarHost

        // obtain any possibly saved state for the experience view model.  See
        // onSaveInstanceState.
        val state: Parcelable? = savedInstanceState?.getParcelable("experienceState")
        roverViewModel = when {
            experienceId != null && campaignId == null -> experienceViewModel(
                rover,
                RoverViewModel.ExperienceRequest.ById(experienceId!!),
                // obtain any possibly saved state for the experience view model.  See
                // onSaveInstanceState.
                state
            )

            experienceId == null && campaignId != null -> experienceViewModel(
                rover,
                RoverViewModel.ExperienceRequest.ByCampaignId(campaignId!!),
                state
            )

            experienceUrl != null -> experienceViewModel(
                rover,
                RoverViewModel.ExperienceRequest.ByCampaignUrl(experienceUrl!!),
                state
            )
            else -> throw RuntimeException(
                "Please pass either one of CAMPAIGN_ID/EXPERIENCE_ID or EXPERIENCE_URL. Consider using RoverActivity.makeIntent()"
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
    protected open fun experienceViewModel(
        rover: Rover,
        experienceRequest: RoverViewModel.ExperienceRequest,
        icicle: Parcelable?
    ): RoverViewModelInterface {
        return Rover.shared?.viewModels?.experienceViewModel(experienceRequest, this.lifecycle) ?: throw RuntimeException("Rover not usable until Rover.initialize has been called.")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        // RoverView owns the toolbar, this is so RoverView can take your menu and include
        // it in its internal toolbar.
        toolbarHost.menu = menu
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // grab the state for the Experience view out of its view model and store it in the
        // activity's own bundle.
        outState.putParcelable("experienceState", roverViewModel?.state)
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun makeIntent(packageContext: Context, experienceId: String?, campaignId: String?, activityClass: Class<out Activity> = RoverActivity::class.java): Intent {
            return Intent(packageContext, activityClass).apply {
                putExtra("EXPERIENCE_ID", experienceId)
                putExtra("CAMPAIGN_ID", campaignId)
            }
        }

        @JvmStatic
        @JvmOverloads
        fun makeIntent(packageContext: Context, experienceUrl: String, activityClass: Class<out Activity> = RoverActivity::class.java): Intent {
            return Intent(packageContext, activityClass).apply {
                putExtra("EXPERIENCE_URL", experienceUrl)
            }
        }
    }
}
