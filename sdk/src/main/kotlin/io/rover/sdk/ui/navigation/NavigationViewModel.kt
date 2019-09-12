package io.rover.sdk.ui.navigation

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.os.Parcelable
import io.rover.sdk.services.EventEmitter
import io.rover.sdk.logging.log
import io.rover.sdk.platform.whenNotNull
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.asPublisher
import io.rover.sdk.streams.flatMap
import io.rover.sdk.streams.map
import io.rover.sdk.streams.shareHotAndReplay
import io.rover.sdk.streams.subscribe
import io.rover.sdk.services.SessionTracker
import io.rover.sdk.data.domain.Experience
import io.rover.sdk.data.domain.Row
import io.rover.sdk.data.domain.Screen
import io.rover.sdk.data.events.RoverEvent
import io.rover.sdk.ui.containers.RoverActivity
import io.rover.sdk.ui.layout.screen.ScreenViewModelInterface
import io.rover.sdk.ui.toolbar.ExperienceToolbarViewModelInterface
import io.rover.sdk.ui.toolbar.ToolbarConfiguration
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
    private val experience: Experience,
    private val campaignId: String?,
    private val eventEmitter: EventEmitter,
    private val sessionTracker: SessionTracker,
    private val resolveScreenViewModel: (screen: Screen) -> ScreenViewModelInterface,
    private val resolveToolbarViewModel: (configuration: ToolbarConfiguration) -> ExperienceToolbarViewModelInterface,
    activityLifecycle: Lifecycle,
    icicle: Parcelable? = null,
    private val initialScreenId: String?
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

    private val toolbarSubject = PublishSubject<ExperienceToolbarViewModelInterface>()
    override val toolbar: Publisher<ExperienceToolbarViewModelInterface> = toolbarSubject.shareHotAndReplay(1)

    private val screenSubject = PublishSubject<NavigationViewModelInterface.ScreenUpdate>()
    override val screen: Publisher<NavigationViewModelInterface.ScreenUpdate> = screenSubject.shareHotAndReplay(1)

    private val backlightSubject = PublishSubject<Boolean>()
    override val backlight: Publisher<Boolean> = backlightSubject.shareHotAndReplay(1)

    private val actions: PublishSubject<Action> = PublishSubject()

    private val screensById = experience.screens.associateBy { it.id }

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

    override var state = if (icicle != null) {
        icicle as State
    } else {
        // the default starting state.  An empty backstack, which the reactive epic below
        // will populate with a an initial back stack frame for the home screen.
        State(
            listOf()
        )
    }
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
                    eventEmitter.trackEvent(
                        RoverEvent.BlockTapped(
                            experience,
                            action.sourceScreenViewModel.screen,
                            action.navigateTo.block,
                            action.row,
                            campaignId
                        )
                    )
                }
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
                                    log.w("Screen by id ${action.navigateTo.screenId} missing from Experience with id ${experience.id}.")
                                }
                                else -> navigateToScreen(screen, screenViewModel, state.backStack, true)
                            }
                        }
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
                        val homeScreen = screensById[initialScreenId] ?: screensById[experience.homeScreenId] ?: throw RuntimeException("Home screen id is dangling.")
                        val screenViewModel = screenViewModelsById[initialScreenId] ?: screenViewModelsById[experience.homeScreenId] ?: throw RuntimeException("Home screen id is dangling.")

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
                                activeScreenViewModel(), false, false
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

        // Dispatch events received from toolbar.
        toolbarSubject.flatMap { it.toolbarEvents }.subscribe { toolbarEvent ->
            actions.onNext(
                when (toolbarEvent) {
                    is ExperienceToolbarViewModelInterface.Event.PressedBack -> Action.PressedBack()
                    is ExperienceToolbarViewModelInterface.Event.PressedClose -> Action.PressedClose()
                }
            )
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
                trackEnterExperience(experience, campaignId)

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
                trackLeaveExperience(experience, campaignId)
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
                    log.e("Screen by id $previousScreenId missing from Experience with id ${experience.id}.")
                    null
                }
                else -> navigateToScreen(screen, screenViewModel, state.backStack, false)
            }
        } ?: closeExperience() // can't go any further back: backstack would be empty; instead emit Exit.
    }

    private fun trackEnterExperience(experience: Experience, campaignId: String?) {
        sessionTracker.enterSession(
            ExperienceSessionKey(experience.id, campaignId),
            RoverEvent.ExperiencePresented(experience, campaignId),
            RoverEvent.ExperienceViewed(experience, campaignId)
        )
    }

    private fun trackLeaveExperience(experience: Experience, campaignId: String?) {
        sessionTracker.leaveSession(
            ExperienceSessionKey(experience.id, campaignId),
            RoverEvent.ExperienceDismissed(experience, campaignId)
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
                ExperienceScreenSessionKey(experience.id, currentScreenId),
                RoverEvent.ScreenDismissed(experience, screenViewModel.screen, campaignId)
            )
        }
    }

    /**
     * Called when entering an Experience screen.  Add any side-effects here, such as tracking the
     * session.
     */
    private fun trackEnterScreen(screenViewModel: ScreenViewModelInterface) {
        sessionTracker.enterSession(
            ExperienceScreenSessionKey(experience.id, screenViewModel.screenId),
            RoverEvent.ScreenPresented(experience, screenViewModel.screen, campaignId),
            RoverEvent.ScreenViewed(experience, screenViewModel.screen, campaignId)
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
        // now take the event from the state change and inject some behavioural transient events
        // into the stream as needed (backlight and toolbar behaviours).
        // emit app bar update and BacklightBoost events (as needed) and into the stream for screen changes.

        backlightSubject.onNext(
            screenViewModel.needsBrightBacklight
        )

        toolbarSubject.onNext(
            resolveToolbarViewModel(screenViewModel.appBarConfiguration)
        )
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
