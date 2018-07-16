package io.rover.experiences.ui.navigation

import android.os.Parcelable
import io.rover.experiences.ui.containers.StandaloneExperienceHostActivity
import io.rover.experiences.ui.layout.screen.ScreenViewModelInterface
import io.rover.experiences.ui.toolbar.ExperienceToolbarViewModelInterface
import io.rover.experiences.ui.toolbar.ToolbarConfiguration
import io.rover.core.data.domain.AttributeValue
import io.rover.core.data.domain.Attributes
import io.rover.core.data.domain.Experience
import io.rover.core.data.domain.Screen
import io.rover.core.events.EventQueueService
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.events.domain.Event
import io.rover.core.logging.log
import io.rover.core.streams.PublishSubject
import io.rover.core.streams.asPublisher
import io.rover.core.streams.flatMap
import io.rover.core.streams.map
import io.rover.core.streams.shareHotAndReplay
import io.rover.core.streams.subscribe
import io.rover.core.tracking.SessionTrackerInterface
import io.rover.core.platform.whenNotNull
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
            val navigateTo: NavigateTo,
            val sourceScreenId: String,
            val sourceRowId: String,
            val sourceBlockId: String
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
            .flatMap { (id, screen) ->
                screen.events.map { Triple(id, screen, it) }
            }
            .subscribe({ (screenId, screen, screenEvent) ->
                // filter out the the events that are not meant for the currently active screen:
                if (activeScreenViewModel() == screen) {
                    actions.onNext(Action.Navigate(screenEvent.navigateTo, screenId, screenEvent.rowId, screenEvent.blockId))
                }
            }, { error -> actions.onError(error) })

        // observe actions and emit analytics events
        actions.subscribe { action ->
            when(action) {
                is Action.Navigate -> {
                    val attributes = when(action.navigateTo) {
                        is NavigateTo.GoToScreenAction -> {
                            hashMapOf(
                                Pair("action", AttributeValue.String("goToScreen")),
                                Pair("destinationScreenID", AttributeValue.String(action.navigateTo.screenId)),
                                // not possible to navigate to a screen in another experience as of yet:
                                Pair("destinationExperienceID", AttributeValue.String(experience.id.rawValue))
                            )
                        }
                        is NavigateTo.PresentWebsiteAction -> {
                            hashMapOf(
                                Pair("action", AttributeValue.String("presentWebsite")),
                                // not possible to navigate to a screen in another experience as of yet:
                                Pair("url", AttributeValue.String(action.navigateTo.url.toString()))
                            )
                        }
                        is NavigateTo.External -> {
                            hashMapOf(
                                Pair("action", AttributeValue.String("openURL"))
                                // TODO: figure out how to properly compose the event data contributed by the Action itself and the context.
                                // Pair("url", AttributeValue.String(action.navigateTo.uri.toString()))
                            )
                        }
                    }

                    val event = Event(
                        name = "Block Tapped",
                        attributes = hashMapOf(
                            Pair("experienceID", AttributeValue.String(experience.id.rawValue)),
                            Pair("screenID", AttributeValue.String(action.sourceScreenId)),
                            Pair("blockID", AttributeValue.String(action.sourceBlockId))
                        ).apply { putAll(attributes) } + attributeHashFragmentForCampaignId()
                    )

                    eventQueueService.trackEvent(event, EventQueueService.ROVER_NAMESPACE)
                }
            }
        }

        // and dispatch the actions to the appropriate methods.
        actions.subscribe { action ->
            when(action) {
                is Action.PressedBack -> {
                    goBack()
                }
                is Action.Navigate -> {
                    when (action.navigateTo) {
                        is NavigateTo.External -> {
                            externalNavigationEvents.onNext(
                                ExperienceExternalNavigationEvent.OpenUri(action.navigateTo.uri)
                            )
                        }
                        is NavigateTo.PresentWebsiteAction -> {
                            externalNavigationEvents.onNext(
                                ExperienceExternalNavigationEvent.PresentWebsite(action.navigateTo.url)
                            )
                        }
                        is NavigateTo.GoToScreenAction -> {
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
                    if(state.backStack.isEmpty()) {
                        // backstack is empty, so we're just starting out.  Navigate forward to the home screen
                        // in the experience!

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

        // session changes from moving between screens or leaving
        screenSubject.subscribe { screenUpdate ->
            trackLeaveScreen()
            trackEnterScreen(screenUpdate.screenViewModel.screenId)
        }
        externalNavigationEvents.subscribe { _ ->
            trackLeaveScreen()
        }
    }

    /**
     * Navigates to a screen, backwards or forwards by emitting the appropriate events,
     * accounting for backstack state.  Updates the backstack state with the change.
     *
     * You can override this to emit a [ExperienceExternalNavigationEvent.Custom] event and thus
     * be able respond to it in your container (say, a subclass of
     * [StandaloneExperienceHostActivity]), and perform your custom behaviour, such as launching an
     * app login screen.
     *
     * TODO: anything I can do to make this particular method any more functional?
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
            if(forwards) {
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

    /**
     * Called when leaving an Experience screen.  Add any side-effects here, such as tracking the
     * session.
     */
    protected fun trackLeaveScreen() {
        // peek the state to get the current screen.
        val currentScreenId = state.backStack.lastOrNull()?.screenId

        if(currentScreenId != null) {
            sessionTracker.leaveSession(
                ExperienceScreenSessionKey(experience.id.rawValue, currentScreenId),
                "Screen Dismissed",
                sessionEventAttributes(currentScreenId)
            )
        }
    }

    /**
     * Called when entering an Experience screen.  Add any side-effects here, such as tracking the
     * session.
     */
    protected fun trackEnterScreen(screenId: String) {
        sessionTracker.enterSession(
            ExperienceScreenSessionKey(experience.id.rawValue, screenId),
            "Screen Presented",
            "Screen Viewed",
            sessionEventAttributes(screenId)
        )
    }

    /**
     * Exits the Experience by emiting the appropriate events.
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

    private fun sessionEventAttributes(screenId: String): Attributes {
        return hashMapOf(
            Pair("experienceID", AttributeValue.String(experience.id.rawValue)),
            Pair("screenID", AttributeValue.String(screenId))
        ) + if(experience.campaignId != null) { hashMapOf(Pair("campaignID", AttributeValue.String(experience.campaignId!!))) } else hashMapOf()
    }

    private fun attributeHashFragmentForCampaignId(): HashMap<String, AttributeValue.String> {
        return if(experience.campaignId != null) hashMapOf(
            Pair("campaignID", AttributeValue.String(experience.campaignId.toString()))
        ) else hashMapOf()
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
     * A unique descriptor for identifying a given experience screen to [SessionTrackerInterface].
     */
    data class ExperienceScreenSessionKey(
        private val experienceId: String,
        private val screenId: String
    )
}
