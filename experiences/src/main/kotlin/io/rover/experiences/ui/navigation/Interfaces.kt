package io.rover.experiences.ui.navigation

import android.app.Activity
import android.os.Parcelable
import android.view.WindowManager
import io.rover.experiences.ui.blocks.concerns.layout.BlockViewModel
import io.rover.experiences.ui.layout.screen.ScreenViewModel
import io.rover.experiences.ui.layout.screen.ScreenViewModelInterface
import io.rover.experiences.ui.toolbar.ExperienceToolbarViewModelInterface
import io.rover.core.ui.concerns.BindableViewModel
import org.reactivestreams.Publisher
import java.net.URI

interface ExperienceNavigationViewModelInterface : BindableViewModel {
    /**
     * Emits when the user should be navigated away to some other piece of content external to the
     * Experience.
     */
    val externalNavigationEvents: Publisher<ExperienceExternalNavigationEvent>


    /**
     * Bind the toolbar display in the view to this publisher.
     *
     * New subscribers are immediately brought up to date.
     */
    val toolbar: Publisher<ExperienceToolbarViewModelInterface>

    data class ScreenUpdate(
        val screenViewModel: ScreenViewModelInterface,
        val backwards: Boolean,
        val animate: Boolean
    )

    /**
     * Observe this publisher to determine what screen view should be displayed, and if its
     * appearance should be animated.
     *
     * New subscribers are immediately brought up to date.
     */
    val screen: Publisher<ScreenUpdate>

    /**
     * Bind the backlight to this publisher.  Signifies that the brightness parameter LayoutParams
     * of the containing window should either be set to either 1 or
     * [WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE].
     *
     * New subscribers are immediately brought up to date.
     */
    val backlight: Publisher<Boolean>

    /**
     * Call when the user has displayed the Experience and should begin navigation.  If this is not
     * called, the user will only see a blank screen!
     */
    fun start()

    /**
     * Call when the user has pressed the back button.
     */
    fun pressBack()

    /**
     * Ask the view model if there are any entries on its internal back stack to revert to.
     *
     * Check this before calling [pressBack].  However, it is optional: if you call pressBack()
     * without checking [canGoBack], and there are no remaining back stack entries remaining, you'll
     * receive an [ExperienceNavigationViewModelInterface.Event.NavigateAway] containing a
     * [ExperienceExternalNavigationEvent.Exit] event.
     */
    fun canGoBack(): Boolean

    /**
     * Obtain a state object for this Experience Navigation View Model.
     */
    val state: Parcelable
}

/**
 * These are navigation that are emitted by the experience navigation view model but because they
 * are for destinations external to the experience they must be passed up by the containing
 * ExperienceViewModel.
 */
sealed class ExperienceExternalNavigationEvent {
    // TODO: we may want to do an (optional) internal web browser like iOS, but there is less call for it
    // because Android has its back button.  Will discuss.

    // TODO: add an Event here for customers to insert a custom navigation event that their own code
    // can handle on the outer side of ExperienceViewModel for navigating to other screens in their
    // app and such.

    /**
     *  Containing view context should launch a web browser for the given URI in the surrounding
     *  navigation flow (such as the general Android backstack, Conductor backstack, etc.) external
     *  to the internal Rover ExperienceNavigationViewModel, whatever it happens to be in the
     *  surrounding app.
     */
    data class OpenUri(val uri: URI) : ExperienceExternalNavigationEvent()

    data class PresentWebsite(val url: URI): ExperienceExternalNavigationEvent()

    /**
     * Containing view context (hosting the Experience) should pop itself ([Activity.finish], etc.)
     * in the surrounding navigation flow (such as the general Android backstack, Conductor
     * backstack, etc.) external to the internal Rover ExperienceNavigationViewModel, whatever it
     * happens to be in the surrounding app.
     */
    class Exit : ExperienceExternalNavigationEvent()

    /**
     * This is a custom navigation type.  It is not used in typical operation of the Rover SDK's
     * Experiences plugin, however, to insert custom behaviour developers may override
     * [ExperienceNavigationViewModel], [ScreenViewModel], or [BlockViewModel] to emit these Custom
     * events thus handle them in their [ExperienceView] container.  A common use case is to peek at
     * incoming screens that have some sort of "needs login" meta property within
     * [ExperienceNavigationViewModel], emit a custom event, and then consuming it within a custom
     * [ExperienceView] to launch your native, non-Rover, login screen.
     *
     * See the documentation for further details.
     */
    data class Custom(val uri: String): ExperienceExternalNavigationEvent()
}
