package io.rover.sdk.ui

import android.os.Parcelable
import android.view.WindowManager
import io.rover.sdk.ui.navigation.ExperienceExternalNavigationEvent
import io.rover.sdk.ui.navigation.NavigationViewModelInterface
import io.rover.sdk.ui.toolbar.ExperienceToolbarViewModelInterface
import io.rover.sdk.ui.toolbar.ViewExperienceToolbar
import io.rover.sdk.ui.concerns.BindableViewModel
import org.reactivestreams.Publisher

/**
 * Responsible for fetching and displaying an Experience, with the appropriate Android toolbar along
 * the top.
 */
interface RoverViewModelInterface : BindableViewModel {
    /**
     * Emits view models that should be bound to the toolbar (which itself should be a
     * [ViewExperienceToolbar]).
     */
    val actionBar: Publisher<ExperienceToolbarViewModelInterface>

    /**
     * This event signifies that the LayoutParams of the containing window should either be set
     * to either 1 or [WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE].
     */
    val extraBrightBacklight: Publisher<Boolean>

    /**
     * Should a loading indicator be displayed?
     */
    val loadingState: Publisher<Boolean>

    /**
     * Emits an experience navigation view model when the Experience is ready to display.
     * Ultimately responsible for displaying the Experience content itself.
     */
    val navigationViewModel: Publisher<NavigationViewModelInterface>

    sealed class Event {
        /**
         * Display an user friendly error message.
         */
        data class DisplayError(
            /**
             * Do not display this message.
             */
            val engineeringReason: String
        ) : Event()

        /**
         * The user should be navigated somewhere external to the experience.
         */
        data class NavigateTo(
            val externalNavigationEvent: ExperienceExternalNavigationEvent
        ) : Event()
    }

    /**
     * One-off events that should not have their side-effects repeated.
     */
    val events: Publisher<Event>

    /**
     * Invoke when user presses back.
     */
    fun pressBack()

    /**
     * Request that the view model will do an initial fetch (or refresh!) of the experience.
     *
     * If you fail to call this nothing will happen.
     */
    fun fetchOrRefresh()

    /**
     * Obtain a state object for this Experience View Model.
     *
     * This view model is the start of the chain of responsibility for any nested view models.
     */
    val state: Parcelable
}
