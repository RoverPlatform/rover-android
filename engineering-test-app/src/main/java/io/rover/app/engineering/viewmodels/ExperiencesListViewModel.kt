package io.rover.app.engineering.viewmodels

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.rover.app.engineering.services.ExperienceRepository
import io.rover.app.engineering.services.NetworkClientResult
import timber.log.Timber

class ExperiencesListViewModel(
    private val selectedFilter: ExperienceListFilter,
    private val experienceRepository: ExperienceRepository
) {
    /**
     * These events reflect the state of the display.
     *
     * In effect, the state of the View should be event-sourced from the stream of these events.
     */
    sealed class Event {
        class NavigateToExperience(val id: String, val name: String): Event()
        class ListStartedRefreshing: Event()
        class ListRefreshed(val events: List<ExperienceRepository.ExperienceListItem>): Event()
        class DisplayProblem(val message: String): Event()
        class ForceLogOut(): Event()
    }

    /**
     * The View should subscribe to this stream and thus be informed of what changes it should make.
     *  See [Event].
     */
    val events: Observable<Event> by lazy { epic.share() }

    /**
     * User has picked an experience to display from the list.
     */
    fun selectExperience(experience: ExperienceRepository.ExperienceListItem) {
        actions.onNext(Action.ExperienceSelected(experience.id, experience.name))
    }

    /**
     * User has pulled down the experience list in a gesture to ask the system to refresh
     * the list from the backend.
     */
    fun requestRefresh() {
        actions.onNext(Action.RefreshRequested())
    }

    private sealed class Action {
        class ExperienceSelected(val id: String, val name: String): Action()
        class RefreshRequested: Action()
    }

    private val actions = PublishSubject.create<Action>()

    /**
     * This pipeline transforms all the expected actions into their appropriate behaviours.
     */
    private val epic: Observable<Event> =
        actions.flatMap { action ->
            when(action) {
                is Action.RefreshRequested -> {
                    Observable.concat(
                        Observable.just(Event.ListStartedRefreshing()),
                        experienceRepository
                            .allExperiences(
                                when(selectedFilter) {
                                    ExperienceListFilter.Draft -> ExperienceRepository.ExperienceFilter.Draft
                                    ExperienceListFilter.Published -> ExperienceRepository.ExperienceFilter.Published
                                }
                            )
                            .toObservable()
                            .map { experienceResult ->
                                when(experienceResult) {
                                    is NetworkClientResult.Success -> Event.ListRefreshed(experienceResult.item)
                                    is NetworkClientResult.Error -> {
                                        if(experienceResult.loginNeeded) {
                                            Event.ForceLogOut()
                                        } else {
                                            Event.DisplayProblem(experienceResult.reason.message ?: "Unknown problem")
                                        }
                                    }
                                }
                            }
                    )
                }
                is Action.ExperienceSelected -> {
                    Observable.just(Event.NavigateToExperience(action.id, action.name))
                }
            }
        }.doOnNext { event ->
            Timber.v("Emitting event: $event")
        }
}

enum class ExperienceListFilter {
    Draft,
    Published
}
