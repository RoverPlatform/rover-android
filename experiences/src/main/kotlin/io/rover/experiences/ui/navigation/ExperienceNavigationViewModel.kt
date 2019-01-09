package io.rover.experiences.ui.navigation

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.os.Parcelable
import io.rover.core.data.domain.AttributeValue
import io.rover.core.data.domain.Attributes
import io.rover.experiences.data.domain.Experience
import io.rover.experiences.data.domain.Screen
import io.rover.core.events.EventQueueService
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.events.domain.Event
import io.rover.core.logging.log
import io.rover.core.platform.whenNotNull
import io.rover.core.streams.PublishSubject
import io.rover.core.streams.asPublisher
import io.rover.core.streams.flatMap
import io.rover.core.streams.map
import io.rover.core.streams.shareHotAndReplay
import io.rover.core.streams.subscribe
import io.rover.core.tracking.SessionTrackerInterface
import io.rover.experiences.data.domain.events.asAttributeValue
import io.rover.experiences.ui.containers.ExperienceActivity
import io.rover.experiences.ui.layout.screen.ScreenViewModelInterface
import io.rover.experiences.ui.toolbar.ExperienceToolbarViewModelInterface
import io.rover.experiences.ui.toolbar.ToolbarConfiguration
import kotlinx.android.parcel.Parcelize
import org.reactivestreams.Publisher

/**
 * Behaviour for navigating through an experience.
 *
 * Responsible for the following concerns: starting at the home screen, maintaining a backstack,
 * state persistence, WebView-like canGoBack/goBack methods, and exposing an API for customizing
 * flow behaviour.
 */
open class ExperienceNavigationViewModel(
    private val experience: Experience,
    private val eventQueueService: EventQueueServiceInterface,
    private val sessionTracker: SessionTrackerInterface,
    private val resolveScreenViewModel: (screen: Screen) -> ScreenViewModelInterface,
    private val resolveToolbarViewModel: (configuration: ToolbarConfiguration) -> ExperienceToolbarViewModelInterface,
    activityLifecycle: Lifecycle,
    icicle: Parcelable? = null
) : ExperienceNavigationViewModelInterface {

    override fun start() {
        actions.onNext(Action.Begin())
    }

    override fun pressBack() {
        actions.onNext(Action.PressedBack())
    }

    override fun canGoBack(): Boolean = state.backStack.size > 1

    final override val externalNavigationEvents: PublishSubject<ExperienceExternalNavigationEvent> =
        PublishSubject()

    private val toolbarSubject = PublishSubject<ExperienceToolbarViewModelInterface>()
    final override val toolbar: Publisher<ExperienceToolbarViewModelInterface> = toolbarSubject.shareHotAndReplay(1)

    private val screenSubject = PublishSubject<ExperienceNavigationViewModelInterface.ScreenUpdate>()
    final override val screen: Publisher<ExperienceNavigationViewModelInterface.ScreenUpdate> = screenSubject.shareHotAndReplay(1)

    private val backlightSubject = PublishSubject<Boolean>()
    final override val backlight: Publisher<Boolean> = backlightSubject.shareHotAndReplay(1)

    private val actions: PublishSubject<Action> = PublishSubject()

    private val screensById = experience.screens.associateBy { it.id.rawValue }

    // TODO: right now we bring up viewmodels for the *entire* experience (ie., all the screens at
    // once).  This is unnecessary.  It should be lazy instead.
    private val screenViewModelsById: Map<String, ScreenViewModelInterface> = screensById.mapValues {
        resolveScreenViewModel(it.value)
    }

    protected sealed class Action {
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
            val rowAttributes: AttributeValue,
            val sourceScreenViewModel: ScreenViewModelInterface
        ) : Action()
    }

    final override var state = if (icicle != null) {
        icicle as State
    } else {
        // the default starting state.  An empty backstack, which the reactive epic below
        // will populate with a an initial back stack frame for the home screen.
        State(
            listOf()
        )
    }
        protected set

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
                    actions.onNext(Action.Navigate(screenEvent.navigateTo, screenEvent.rowAttributes, screenViewModel))
                }
            }, { error -> actions.onError(error) })

        // observe actions and emit analytics events
        actions.subscribe { action ->

            when (action) {
                is Action.Navigate -> {
                    val attributes = hashMapOf(
                        Pair("experience", experience.asAttributeValue()),
                        Pair("screen", action.sourceScreenViewModel.attributes),
                        Pair("block", action.navigateTo.blockAttributes),
                        Pair("row", action.rowAttributes)
                    )

                    val event = Event(
                        name = "Block Tapped",
                        attributes = attributes
                    )

                    eventQueueService.trackEvent(event, EventQueueService.ROVER_NAMESPACE)
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
                                    log.w("Screen by id ${action.navigateTo.screenId} missing from Experience with id ${experience.id.rawValue}.")
                                }
                                else -> navigateToScreen(screen, screenViewModel, state.backStack, true)
                            }
                        }
                    }
                }
                is Action.PressedClose -> {
                    closeExperience(state.backStack)
                }
                is Action.Begin -> {
                    if (state.backStack.isEmpty()) {
                        // backstack is empty, so we're just starting out.  Navigate forward to the
                        // home screen in the experience!

                        val homeScreen = screensById[experience.homeScreenId.rawValue] ?: throw RuntimeException("Home screen id is dangling.")
                        val screenViewModel = screenViewModelsById[experience.homeScreenId.rawValue] ?: throw RuntimeException("Home screen id is dangling.")

                        navigateToScreen(
                            homeScreen,
                            screenViewModel,
                            state.backStack,
                            true
                        )
                    } else {
                        // just direct subscribers to the screen last left in the state.
                        screenSubject.onNext(
                            ExperienceNavigationViewModelInterface.ScreenUpdate(
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
                trackEnterExperience(experience)

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
                trackLeaveExperience(experience)
            }
        })
    }

    /**
     * Navigates to a screen, backwards or forwards by emitting the appropriate events,
     * accounting for backstack state.  Updates the backstack state with the change.
     *
     * You can override this to emit a [ExperienceExternalNavigationEvent.Custom] event and thus
     * be able respond to it in your container (say, a subclass of
     * [ExperienceActivity]), and perform your custom behaviour, such as launching an
     * app login screen.
     */
    protected open fun navigateToScreen(
        screen: Screen,
        screenViewModel: ScreenViewModelInterface,
        currentBackStack: List<BackStackFrame>,
        forwards: Boolean
    ) {
        screenSubject.onNext(
            ExperienceNavigationViewModelInterface.ScreenUpdate(
                screenViewModel,
                !forwards,
                currentBackStack.isNotEmpty()
            )
        )

        state = State(
            if (forwards) {
                currentBackStack + listOf(BackStackFrame(screen.id.rawValue))
            } else {
                currentBackStack.subList(0, currentBackStack.lastIndex)
            }
        )
    }

    /**
     * Navigate backwards, if possible, or exit entirely if the backstack has been exhausted.
     */
    protected open fun goBack() {
        val possiblePreviousScreenId = state.backStack.getOrNull(state.backStack.lastIndex - 1)?.screenId
        possiblePreviousScreenId.whenNotNull { previousScreenId ->
            val screenViewModel = screenViewModelsById[previousScreenId]
            val screen = screensById[previousScreenId]

            when {
                screenViewModel == null || screen == null -> {
                    log.e("Screen by id $previousScreenId missing from Experience with id ${experience.id.rawValue}.")
                    null
                }
                else -> navigateToScreen(screen, screenViewModel, state.backStack, false)
            }
        } ?: closeExperience(state.backStack) // can't go any further back: backstack would be empty; instead emit Exit.
    }

    protected fun trackEnterExperience(experience: Experience) {
        sessionTracker.enterSession(
            ExperienceSessionKey(experience.id.rawValue, experience.campaignId),
            "Experience Presented",
            "Experience Viewed",
            sessionExperienceEventAttributes(experience)
        )
    }

    protected fun trackLeaveExperience(experience: Experience) {
        sessionTracker.leaveSession(
            ExperienceSessionKey(experience.id.rawValue, experience.campaignId),
            "Experience Dismissed",
            sessionExperienceEventAttributes(experience)
        )
    }

    /**
     * Called when leaving an Experience screen.  Add any side-effects here, such as tracking the
     * session.
     */
    protected fun trackLeaveScreen() {
        // peek the state to determine if a screen view model is active.
        val currentScreenId = state.backStack.lastOrNull()?.screenId

        if (currentScreenId != null) {
            val screenViewModel = activeScreenViewModel()
            sessionTracker.leaveSession(
                ExperienceScreenSessionKey(experience.id.rawValue, currentScreenId),
                "Screen Dismissed",
                sessionScreenEventAttributes(screenViewModel)
            )
        }
    }

    /**
     * Called when entering an Experience screen.  Add any side-effects here, such as tracking the
     * session.
     */
    protected fun trackEnterScreen(screenViewModel: ScreenViewModelInterface) {
        sessionTracker.enterSession(
            ExperienceScreenSessionKey(experience.id.rawValue, screenViewModel.screenId),
            "Screen Presented",
            "Screen Viewed",
            sessionScreenEventAttributes(screenViewModel)
        )
    }

    /**
     * Exits the Experience by emitting the appropriate events.
     *
     * You can override this to modify the exit behaviour, perhaps to emit a
     * [ExperienceNavigationViewModelInterface.Emission.Event.NavigateAway] with a
     * [ExperienceExternalNavigationEvent.Custom] to inform your main Activity to instead perform no
     * effect at all.
     */
    protected open fun closeExperience(
        currentBackStack: List<BackStackFrame>
    ) {
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

    protected open fun sessionExperienceEventAttributes(experience: Experience): Attributes {
        return hashMapOf(
            Pair("experience", experience.asAttributeValue())
        )
    }

    protected open fun sessionScreenEventAttributes(screenViewModel: ScreenViewModelInterface): Attributes {
        return hashMapOf(
            Pair("experience", experience.asAttributeValue()),
            Pair("screen", screenViewModel.attributes)
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
