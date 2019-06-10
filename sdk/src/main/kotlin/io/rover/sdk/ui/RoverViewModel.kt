package io.rover.sdk.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Parcelable
import io.rover.sdk.R
import io.rover.sdk.data.graphql.GraphQlApiService
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.Publishers
import io.rover.sdk.streams.Scheduler
import io.rover.sdk.streams.doOnSubscribe
import io.rover.sdk.streams.flatMap
import io.rover.sdk.streams.map
import io.rover.sdk.streams.observeOn
import io.rover.sdk.streams.share
import io.rover.sdk.streams.shareAndReplay
import io.rover.sdk.streams.subscribe
import io.rover.sdk.data.domain.Experience
import io.rover.sdk.data.graphql.ApiResult
import io.rover.sdk.data.operations.data.decodeJson
import io.rover.sdk.data.operations.FetchExperienceRequest
import io.rover.sdk.data.operations.data.decodeJson
import io.rover.sdk.ui.navigation.ExperienceExternalNavigationEvent
import io.rover.sdk.ui.navigation.NavigationViewModelInterface
import io.rover.sdk.ui.toolbar.ExperienceToolbarViewModelInterface
import io.rover.sdk.ui.toolbar.ToolbarConfiguration
import kotlinx.android.parcel.Parcelize
import org.json.JSONObject
import org.reactivestreams.Publisher

internal class RoverViewModel(
    private val experienceRequest: ExperienceRequest,
    private val graphQlApiService: GraphQlApiService,
    private val mainThreadScheduler: Scheduler,
    private val resolveNavigationViewModel: (experience: Experience, icicle: Parcelable?) -> NavigationViewModelInterface,
    private val icicle: Parcelable? = null,
    private val experienceTransformer: ((Experience) -> Experience)? = null
) : RoverViewModelInterface {

    override val state: Parcelable
        get() {
            // this is a slightly strange arrangement: since (almost) all state is really within a
            // contained view model (that only becomes available synchronously) we effectively
            // assume we have only our icicle state when that nested view model is not available.
            return if (currentNavigationViewModel == null) {
                // getting saved early, starting over.
                icicle ?: State(null)
            } else {
                State(
                    currentNavigationViewModel?.state
                )
            }
        }

    override val actionBar: Publisher<ExperienceToolbarViewModelInterface>
    override val extraBrightBacklight: Publisher<Boolean>
    override val navigationViewModel: Publisher<NavigationViewModelInterface>
    override val events: Publisher<RoverViewModelInterface.Event>
    override val loadingState: Publisher<Boolean>

    override fun pressBack() {
        if (currentNavigationViewModel == null) {
            actionSource.onNext(Action.BackPressedBeforeExperienceReady)
        } else {
            currentNavigationViewModel?.pressBack()
        }
    }

    override fun fetchOrRefresh() {
        actionSource.onNext(Action.Fetch)
    }

    private val actionSource = PublishSubject<Action>()
    private val actions = actionSource.share()

    private fun fetchExperience(): Publisher<out ApiResult<Experience>> =
        graphQlApiService.fetchExperience(
            when (experienceRequest) {
                is ExperienceRequest.ByUrl -> FetchExperienceRequest.ExperienceQueryIdentifier.ByUniversalLink(experienceRequest.url)
                is ExperienceRequest.ById -> FetchExperienceRequest.ExperienceQueryIdentifier.ById(experienceRequest.experienceId)
            }).observeOn(mainThreadScheduler)

    /**
     * Hold on to a reference to the navigation view model so that it can contribute to the Android
     * state restore parcelable.
     */
    private var currentNavigationViewModel: NavigationViewModelInterface? = null

    init {
        // maybe for each type fork I should split out and delegate to subjects?
        val fetchAttempts = PublishSubject<ApiResult<Experience>>()

        val toolBarSubject = PublishSubject<ExperienceToolbarViewModelInterface>()
        val loadingSubject = PublishSubject<Boolean>()
        val eventsSubject = PublishSubject<RoverViewModelInterface.Event>()

        actions.subscribe { action ->
            when (action!!) {
                Action.BackPressedBeforeExperienceReady -> {
                    eventsSubject.onNext(RoverViewModelInterface.Event.NavigateTo(
                        ExperienceExternalNavigationEvent.Exit()
                    ))
                }
                Action.Fetch -> {
                    fetchExperience()
                        .doOnSubscribe {
                            loadingSubject.onNext(true)

                            // emit a temporary toolbar.
                            toolBarSubject.onNext(
                                object : ExperienceToolbarViewModelInterface {
                                    override val toolbarEvents: Publisher<ExperienceToolbarViewModelInterface.Event>
                                        get() = Publishers.empty()

                                    override val configuration: ToolbarConfiguration
                                        get() = ToolbarConfiguration(
                                            true,
                                            "",
                                            Color.BLUE,
                                            Color.BLUE,
                                            Color.BLUE,
                                            true,
                                            false,
                                            Color.BLUE
                                        )

                                    override fun pressedBack() {
                                        actionSource.onNext(Action.BackPressedBeforeExperienceReady)
                                    }

                                    override fun pressedClose() {
                                        actionSource.onNext(Action.BackPressedBeforeExperienceReady)
                                    }
                                }
                            )
                        }
                        .subscribe(fetchAttempts::onNext)
                }
            }
        }

        val experiences = PublishSubject<Experience>()

        fetchAttempts.subscribe { networkResult ->
           loadingSubject.onNext(false)
           when (networkResult) {
               is ApiResult.Error -> {
                   eventsSubject.onNext(RoverViewModelInterface.Event.DisplayError(
                       networkResult.throwable.message ?: "Unknown"
                   ))
               }
               is ApiResult.Success -> {
                   experiences.onNext(networkResult.response)
               }
           }
        }

        // yields an experience navigation view model. used by both our view and some of the
        // internal subscribers below.
        navigationViewModel = experiences.map { experience ->
            val transformedExperience = experienceTransformer?.invoke(experience) ?: experience

            resolveNavigationViewModel(
                transformedExperience,
                // allow it to restore from state if there is any.
                (state as State).navigationState
            ).apply {
                // store a reference to the view model in object scope so it can contribute to the
                // state parcelable.
                this@RoverViewModel.currentNavigationViewModel = this
            }
        }.shareAndReplay(1)

        // filter out all the navigation view model events external navigation events.
        val navigateAwayEvents = navigationViewModel.flatMap { navigationViewModel ->
            navigationViewModel.externalNavigationEvents
        }

        // emit those navigation events to our view.
        navigateAwayEvents.subscribe { navigateAway ->
            eventsSubject.onNext(
                RoverViewModelInterface.Event.NavigateTo(navigateAway)
            )
        }

        // pass through the backlight and toolbar updates.
        val backlightEvents = navigationViewModel.flatMap { navigationViewModel ->
            navigationViewModel.backlight
        }
        val toolbarEvents = navigationViewModel.flatMap { navigationViewModel ->
            navigationViewModel.toolbar
        }
        val backlightSubject = PublishSubject<Boolean>()
        backlightEvents.subscribe { backlightSubject.onNext(it) }
        toolbarEvents.subscribe { toolBarSubject.onNext(it) }

        actionBar = toolBarSubject.shareAndReplay(1).observeOn(mainThreadScheduler)
        extraBrightBacklight = backlightSubject.shareAndReplay(1).observeOn(mainThreadScheduler)
        loadingState = loadingSubject.shareAndReplay(1).observeOn(mainThreadScheduler)
        events = eventsSubject.shareAndReplay(0).observeOn(mainThreadScheduler)
    }

    enum class Action {
        /**
         * Back pressed before the experience navigation view model became available.  We can't
         * deliver the back press event to it as we would normally do; instead we'll handle this
         * case as an event ourselves and emit an Exit event for it instead.
         */
        BackPressedBeforeExperienceReady,

        /**
         * Fetch (or refresh) the displayed experience.
         */
        Fetch
    }

    // @Parcelize Kotlin synthetics are generating the CREATOR method for us.
    @SuppressLint("ParcelCreator")
    @Parcelize
    data class State(
        val navigationState: Parcelable?
    ) : Parcelable

    sealed class ExperienceRequest {
        data class ByUrl(val url: String) : ExperienceRequest()
        data class ById(val experienceId: String) : ExperienceRequest()
    }
}
