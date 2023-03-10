/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.experiences.classic.navigation

import android.os.Parcelable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import io.rover.sdk.core.data.domain.ClassicExperienceModel
import io.rover.sdk.core.data.domain.Row
import io.rover.sdk.core.data.domain.Screen
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.streams.*
import io.rover.sdk.experiences.classic.layout.screen.ScreenViewModelInterface
import io.rover.sdk.experiences.classic.toolbar.ToolbarConfiguration
import io.rover.sdk.experiences.data.events.MiniAnalyticsEvent
import io.rover.sdk.experiences.platform.whenNotNull
import io.rover.sdk.experiences.services.ClassicEventEmitter
import io.rover.sdk.experiences.services.SessionTracker
import kotlinx.android.parcel.Parcelize
import org.reactivestreams.Publisher

/**
 * Behaviour for navigating through an experience.
 *
 * Responsible for the following concerns: starting at the home screen, maintaining a backstack,
 * state persistence, WebView-like canGoBack/goBack methods, and exposing an API for customizing
 * flow behaviour.
 */
internal class NavigationViewModel(
    private val classicExperience: ClassicExperienceModel,
    private val campaignId: String?,
    private val classicEventEmitter: ClassicEventEmitter,
    private val sessionTracker: SessionTracker,
    private val resolveScreenViewModel: (screen: Screen) -> ScreenViewModelInterface,
    private val initialScreenId: String?,
    activityLifecycle: Lifecycle,
    icicle: Parcelable? = null
) : NavigationViewModelInterface {

    override fun start() {
        actions.onNext(Action.Begin())
    }

    override fun pressBack() {
        actions.onNext(Action.PressedBack())
    }

    override fun canGoBack(): Boolean = state.backStack.size > 1

    override val externalNavigationEvents: PublishSubject<ExperienceExternalNavigationEvent> =
        PublishSubject()

    override var toolbar: ToolbarConfiguration? by mutableStateOf(null)

    private val screenSubject = PublishSubject<NavigationViewModelInterface.ScreenUpdate>()
    override val screen: Publisher<NavigationViewModelInterface.ScreenUpdate> = screenSubject.shareHotAndReplay(1)

    override val backlight: Boolean by mutableStateOf(false)

    private val actions: PublishSubject<Action> = PublishSubject()

    private val screensById = classicExperience.screens.associateBy { it.id }

    // TODO: right now we bring up viewmodels for the *entire* experience (ie., all the screens at
    // once).  This is unnecessary.  It should be lazy instead.
    private val screenViewModelsById: Map<String, ScreenViewModelInterface> = screensById.mapValues {
        resolveScreenViewModel(it.value)
    }

    private sealed class Action {
        /**
         * The user has opened the navigation and wishes to begin viewing it.
         */
        class Begin : Action()
        class PressedBack : Action()

        /**
         * Emitted into the actions stream if the close button is pressed on the toolbar.
         */
        class PressedClose : Action()
        class Navigate(
            val navigateTo: NavigateToFromBlock,
            val row: Row,
            val sourceScreenViewModel: ScreenViewModelInterface
        ) : Action()
    }

    override var state by mutableStateOf(State(listOf()))
        private set

    init {
        // wire up subscribers to connect our input PublishSubjects to the output Publishers through
        // overridable methods.

        // This subscriber listens to all the view models and then dispatches their navigation events
        // to our actions publisher.
        screenViewModelsById
            .entries
            .asPublisher()
            .flatMap { (_, screen) ->
                screen.events.map { Pair(screen, it) }
            }
            .subscribe({ (screenViewModel, screenEvent) ->
                // filter out the the events that are not meant for the currently active screen:
                if (activeScreenViewModel() == screenViewModel) {
                    actions.onNext(Action.Navigate(screenEvent.navigateTo, screenEvent.row, screenViewModel))
                }
            }, { error -> actions.onError(error) })

        // observe actions and emit analytics events
        actions.subscribe { action ->
            when (action) {
                is Action.Navigate -> {
                    classicEventEmitter.trackEvent(
                        MiniAnalyticsEvent.BlockTapped(
                            classicExperience,
                            action.sourceScreenViewModel.screen,
                            action.navigateTo.block,
                            action.row,
                            campaignId
                        )
                    )
                }
                else -> { /* no-op */ }
            }
        }

        // and dispatch the actions to the appropriate methods.
        actions.subscribe { action ->
            when (action) {
                is Action.PressedBack -> {
                    goBack()
                }
                is Action.Navigate -> {
                    when (action.navigateTo) {
                        is NavigateToFromBlock.External -> {
                            externalNavigationEvents.onNext(
                                ExperienceExternalNavigationEvent.OpenUri(action.navigateTo.uri)
                            )
                        }
                        is NavigateToFromBlock.PresentWebsiteAction -> {
                            externalNavigationEvents.onNext(
                                ExperienceExternalNavigationEvent.PresentWebsite(action.navigateTo.url)
                            )
                        }
                        is NavigateToFromBlock.GoToScreenAction -> {
                            val screenViewModel = screenViewModelsById[action.navigateTo.screenId]
                            val screen = screensById[action.navigateTo.screenId]

                            when {
                                screenViewModel == null || screen == null -> {
                                    log.w("Screen by id ${action.navigateTo.screenId} missing from Experience with id ${classicExperience.id}.")
                                }
                                else -> navigateToScreen(screen, screenViewModel, state.backStack, true)
                            }
                        }
                        else -> { /* no-op */ }
                    }
                }
                is Action.PressedClose -> {
                    closeExperience()
                }
                is Action.Begin -> {
                    if (state.backStack.isEmpty()) {
                        // backstack is empty, so we're just starting out.  Navigate forward to the
                        // home screen in the experience!

                        // If no initial screen set then use the default home screen
                        val homeScreen = screensById[initialScreenId] ?: screensById[classicExperience.homeScreenId] ?: throw RuntimeException("Home screen id is dangling.")
                        val screenViewModel = screenViewModelsById[initialScreenId] ?: screenViewModelsById[classicExperience.homeScreenId] ?: throw RuntimeException("Home screen id is dangling.")

                        navigateToScreen(
                            homeScreen,
                            screenViewModel,
                            state.backStack,
                            true
                        )
                    } else {
                        // just direct subscribers to the screen last left in the state.
                        screenSubject.onNext(
                            NavigationViewModelInterface.ScreenUpdate(
                                activeScreenViewModel(),
                                false,
                                false
                            )
                        )
                    }
                }
            }
        }

        // Dispatch backlight and toolbar updates for any screen.
        screenSubject.subscribe { screenUpdate ->
            emitToolbarAndBacklightEventsForScreen(screenUpdate.screenViewModel)
        }

        // session changes from moving between screens.
        screenSubject.subscribe { screenUpdate ->
            trackLeaveScreen()
            trackEnterScreen(screenUpdate.screenViewModel)
        }

        // handle visibility changes for session tracking:

        activityLifecycle.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
            fun presented() {
                trackEnterExperience(classicExperience, campaignId)

                // if an experience screen is already active (which can happen if the containing
                // Activity is resumed without having been entirely destroyed), then handle
                // re-emitting the trackEnterScreen event.
                activeScreenViewModelIfPresent().whenNotNull { activeScreen ->
                    trackEnterScreen(
                        activeScreen
                    )
                }
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            fun dismissed() {
                trackLeaveScreen()
                trackLeaveExperience(classicExperience, campaignId)
            }
        })
    }

    /**
     * Navigates to a screen, backwards or forwards by emitting the appropriate events,
     * accounting for backstack state.  Updates the backstack state with the change.
     *
     * You can override this to emit a [ExperienceExternalNavigationEvent.Custom] event and thus
     * be able respond to it in your container (say, a subclass of
     * [RoverActivity]), and perform your custom behaviour, such as launching an
     * app login screen.
     */
    private fun navigateToScreen(
        screen: Screen,
        screenViewModel: ScreenViewModelInterface,
        currentBackStack: List<BackStackFrame>,
        forwards: Boolean
    ) {
        screenSubject.onNext(
            NavigationViewModelInterface.ScreenUpdate(
                screenViewModel,
                !forwards,
                currentBackStack.isNotEmpty()
            )
        )

        state = State(
            if (forwards) {
                currentBackStack + listOf(BackStackFrame(screen.id))
            } else {
                currentBackStack.subList(0, currentBackStack.lastIndex)
            }
        )
    }

    override fun toolbarPressBack() {
        actions.onNext(Action.PressedBack())
    }

    override fun toolbarPressClose() {
        actions.onNext(Action.PressedClose())
    }

    /**
     * Navigate backwards, if possible, or exit entirely if the backstack has been exhausted.
     */
    private fun goBack() {
        val possiblePreviousScreenId = state.backStack.getOrNull(state.backStack.lastIndex - 1)?.screenId
        possiblePreviousScreenId.whenNotNull { previousScreenId ->
            val screenViewModel = screenViewModelsById[previousScreenId]
            val screen = screensById[previousScreenId]

            when {
                screenViewModel == null || screen == null -> {
                    log.e("Screen by id $previousScreenId missing from Experience with id ${classicExperience.id}.")
                    null
                }
                else -> navigateToScreen(screen, screenViewModel, state.backStack, false)
            }
        } ?: closeExperience() // can't go any further back: backstack would be empty; instead emit Exit.
    }

    private fun trackEnterExperience(classicExperience: ClassicExperienceModel, campaignId: String?) {
        sessionTracker.enterSession(
            ExperienceSessionKey(classicExperience.id, campaignId),
            MiniAnalyticsEvent.ExperiencePresented(classicExperience, campaignId),
            MiniAnalyticsEvent.ExperienceViewed(classicExperience, campaignId)
        )
    }

    private fun trackLeaveExperience(classicExperience: ClassicExperienceModel, campaignId: String?) {
        sessionTracker.leaveSession(
            ExperienceSessionKey(classicExperience.id, campaignId),
            MiniAnalyticsEvent.ExperienceDismissed(classicExperience, campaignId)
        )
    }

    /**
     * Called when leaving an Experience screen.  Add any side-effects here, such as tracking the
     * session.
     */
    private fun trackLeaveScreen() {
        // peek the state to determine if a screen view model is active.
        val currentScreenId = state.backStack.lastOrNull()?.screenId

        if (currentScreenId != null) {
            val screenViewModel = activeScreenViewModel()
            sessionTracker.leaveSession(
                ExperienceScreenSessionKey(classicExperience.id, currentScreenId),
                MiniAnalyticsEvent.ScreenDismissed(classicExperience, screenViewModel.screen, campaignId)
            )
        }
    }

    /**
     * Called when entering an Experience screen.  Add any side-effects here, such as tracking the
     * session.
     */
    private fun trackEnterScreen(screenViewModel: ScreenViewModelInterface) {
        sessionTracker.enterSession(
            ExperienceScreenSessionKey(classicExperience.id, screenViewModel.screenId),
            MiniAnalyticsEvent.ScreenPresented(classicExperience, screenViewModel.screen, campaignId),
            MiniAnalyticsEvent.ScreenViewed(classicExperience, screenViewModel.screen, campaignId)
        )
    }

    /**
     * Exits the Experience by emitting the appropriate events.
     *
     * You can override this to modify the exit behaviour, perhaps to emit a
     * [NavigationViewModelInterface.Emission.Event.NavigateAway] with a
     * [ExperienceExternalNavigationEvent.Custom] to inform your main Activity to instead perform no
     * effect at all.
     */
    private fun closeExperience() {
        externalNavigationEvents.onNext(
            ExperienceExternalNavigationEvent.Exit()
        )
    }

    private fun activeScreenViewModel(): ScreenViewModelInterface {
        val currentScreenId = state.backStack.lastOrNull()?.screenId ?: throw RuntimeException("Backstack unexpectedly empty")
        return screenViewModelsById[currentScreenId] ?: throw RuntimeException("Unexpectedly found a dangling screen id in the back stack.")
    }

    private fun activeScreenViewModelIfPresent(): ScreenViewModelInterface? {
        val currentScreenId = state.backStack.lastOrNull()?.screenId ?: return null
        return screenViewModelsById[currentScreenId]
    }

    /**
     * In response to navigating between screens, we want to inject events for setting the backlight
     * boost and/or the toolbar.
     */
    private fun emitToolbarAndBacklightEventsForScreen(screenViewModel: ScreenViewModelInterface) {
        toolbar = screenViewModel.appBarConfiguration
    }

    @Parcelize
    data class BackStackFrame(
        val screenId: String
    ) : Parcelable

    @Parcelize
    data class State(
        val backStack: List<BackStackFrame>
    ) : Parcelable

    /**
     * A unique descriptor for identifying a given experience screen to the [SessionTrackerInterface].
     */
    data class ExperienceScreenSessionKey(
        private val experienceId: String,
        private val screenId: String
    )

    /**
     * A unique descriptor for identifying a given experience to the [SessionTrackerInterface].
     */
    data class ExperienceSessionKey(
        val experienceId: String,
        val campaignId: String?
    )
}
