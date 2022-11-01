package io.rover.experiences.ui.blocks.poll

import android.os.Handler
import io.rover.core.data.graphql.putProp
import io.rover.core.data.graphql.safeGetString
import io.rover.experiences.data.graphql.ApiResult
import io.rover.experiences.logging.log
import io.rover.core.streams.PublishSubject
import io.rover.core.streams.Scheduler
import io.rover.core.streams.first
import io.rover.core.streams.map
import io.rover.core.streams.observeOn
import io.rover.core.streams.subscribe
import org.json.JSONObject
import org.reactivestreams.Publisher
import org.reactivestreams.Subscription

internal class VotingInteractor(
    private val votingService: VotingService,
    private val votingStorage: VotingStorage,
    private val mainScheduler: Scheduler
) {
    val votingState = PublishSubject<VotingState>()
    private var pollId: String = ""
    private var optionIds = listOf<String>()
    private val subscriptions = mutableListOf<Subscription>()

    private var currentState: VotingState = VotingState.InitialState
        set(value) {
            field = value
            votingState.onNext(value)
            votingStorage.setLastSeenPollState(pollId, value)

            when (value) {
                is VotingState.InitialState -> startFetchingResults(pollId, optionIds)
                is VotingState.ResultsSeeded -> {}
                is VotingState.PollAnswered -> startFetchingResults(pollId, optionIds)
                is VotingState.SubmittingAnswer -> submitAnswer(value)
                is VotingState.RefreshingResults -> refreshResults()
            }
        }

    private var cancelled = true

    fun cancel() {
        refreshResultsHandler.removeCallbacksAndMessages(null)
        resultsRetrievalHandler.removeCallbacksAndMessages(null)
        submitAnswerHandler.removeCallbacksAndMessages(null)
        currentUpdateSubscription?.cancel()
        subscriptions.forEach { it.cancel() }
        cancelled = true
    }

    companion object {
        private const val REFRESHING_RESULTS_DELAY = 5000L
        private const val RESULTS_RETRIEVAL_BACKOFF_DELAY = 2000L
        private const val VOTE_SUBMISSION_BACKOFF_DELAY = 2000L
    }

    fun initialize(pollId: String, optionIds: List<String>) {
        this.pollId = pollId
        this.optionIds = optionIds

        currentState = votingStorage.getLastSeenPollState(pollId).determineInitialState(optionIds)
        cancelled = false
    }

    private fun startFetchingResults(pollId: String, optionIds: List<String>) {
        resultsRetrievalHandler.removeCallbacksAndMessages(null)

        fetchVotingResults(optionIds).observeOn(mainScheduler).first().subscribe({ optionResults ->
            if (optionResults.results.isNotEmpty()) {
                when (val state = currentState) {
                    is VotingState.PollAnswered -> currentState = state.transitionToSubmittingAnswer(optionResults, true)
                    is VotingState.InitialState -> currentState = state.transitionToResultsSeeded(optionResults)
                    else -> { /* no-op */ }
                }
            } else {
                createRetrieveResultsBackoff(pollId, optionIds)
            }
        }, {
            createRetrieveResultsBackoff(pollId, optionIds)
        }, { subscription ->
            if (cancelled) {
                subscription.cancel()
            } else {
                resultRetrievalSubscription = subscription
                subscriptions.add(subscription)
            }
        })
    }

    fun castVotes(optionId: String) {
        when (val state = currentState) {
            is VotingState.ResultsSeeded ->
                currentState =
                    state.transitionToSubmittingAnswer(optionId, true)
            is VotingState.InitialState -> currentState = state.transitionToPollAnswered(optionId)
            else -> { /* no-op */ }
        }
    }

    private fun submitAnswer(submittingAnswer: VotingState.SubmittingAnswer) {
        submitAnswerHandler.removeCallbacksAndMessages(null)

        votingService.castVote(pollId, submittingAnswer.selectedOption).observeOn(mainScheduler)
            .first().subscribe({ voteOutcome ->
                val state = currentState
                if (voteOutcome is VoteOutcome.VoteSuccess) {
                    if (state is VotingState.SubmittingAnswer) currentState =
                        state.transitionToRefreshingResults(state.selectedOption, state.optionResults)
                } else {
                    createVoteSenderBackoff(submittingAnswer)
                }
            }, { createVoteSenderBackoff(submittingAnswer) }, {
                if (cancelled) it.cancel() else subscriptions.add(it)
            })
    }

    private fun votingResultsUpdate(optionIds: List<String>) {
        refreshResultsHandler.removeCallbacksAndMessages(null)

        fetchVotingResults(optionIds).observeOn(mainScheduler).first().subscribe(
            { fetchedOptionResults ->
                val state = currentState
                if (state is VotingState.RefreshingResults && fetchedOptionResults.results.isNotEmpty()) {
                    currentState = state.transitionToRefreshingResults(
                        changeVotesToPercentages(fetchedOptionResults),
                        shouldTransition = true,
                        shouldAnimate = true
                    )
                }
            },
            { if (currentState is VotingState.RefreshingResults) refreshResults() },
            { subscription ->
                if (cancelled) {
                    subscription.cancel()
                } else {
                    currentUpdateSubscription = subscription
                    subscriptions.add(subscription)
                }
            }
        )
    }

    private fun fetchVotingResults(optionIds: List<String>): Publisher<OptionResults> {
        return votingService.fetchResults(pollId, optionIds).map {
            if (it is ApiResult.Success<OptionResults>) it.response else OptionResults(mapOf())
        }
    }

    private val resultsRetrievalHandler = Handler()
    private var resultsRetrievalTimesInvoked: Int = 0
    private var resultRetrievalSubscription: Subscription? = null
        set(value) {
            value?.cancel()
            field = value
        }

    private val submitAnswerHandler = Handler()
    private var submitAnswerTimesInvoked = 0

    private var currentUpdateSubscription: Subscription? = null
        set(value) {
            value?.cancel()
            field = value
        }

    private val refreshResultsHandler = Handler()

    private fun createVoteSenderBackoff(submittingAnswer: VotingState.SubmittingAnswer) {
        submitAnswerHandler.removeCallbacksAndMessages(null)
        submitAnswerHandler.postDelayed({
            try {
                log.v("vote sent")
                submitAnswerTimesInvoked++
                submitAnswer(submittingAnswer)
            } catch (e: Exception) {
                log.w("issue trying to send vote results $e")
            }
        }, VOTE_SUBMISSION_BACKOFF_DELAY * submitAnswerTimesInvoked)
    }

    private fun createRetrieveResultsBackoff(pollId: String, optionIds: List<String>) {
        resultsRetrievalHandler.removeCallbacksAndMessages(null)
        resultsRetrievalHandler.postDelayed({
            try {
                resultsRetrievalTimesInvoked++
                startFetchingResults(pollId, optionIds)
            } catch (e: Exception) {
                log.w("issue trying to retrieve results $e")
            }
        }, RESULTS_RETRIEVAL_BACKOFF_DELAY * resultsRetrievalTimesInvoked)
    }

    private fun refreshResults() {
        refreshResultsHandler.removeCallbacksAndMessages(null)
        refreshResultsHandler.postDelayed({
            try {
                votingResultsUpdate(optionIds)
            } catch (e: Exception) {
                log.w("issue trying to send vote results $e")
            }
        }, REFRESHING_RESULTS_DELAY)
    }
}

internal sealed class VotingState {
    abstract fun encodeJson(): JSONObject
    abstract fun determineInitialState(optionIds: List<String>): VotingState

    object InitialState : VotingState() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                put(TYPE, INITIAL_STATE)
            }
        }

        fun transitionToResultsSeeded(results: OptionResults) = ResultsSeeded(results)
        fun transitionToPollAnswered(selectedOption: String) = PollAnswered(selectedOption)

        override fun determineInitialState(optionIds: List<String>) = InitialState
    }

    data class ResultsSeeded(val optionResults: OptionResults) : VotingState() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                put(TYPE, RESULTS_SEEDED)
                putProp(this@ResultsSeeded, ResultsSeeded::optionResults) { it.encodeJson() }
            }
        }

        fun transitionToSubmittingAnswer(
            selectedOption: String,
            shouldAnimate: Boolean
        ): SubmittingAnswer {
            val optionResultsWithIncrementedOption = optionResults.results.toMutableMap()
                .apply { set(selectedOption, (get(selectedOption) ?: 0) + 1) }
            return SubmittingAnswer(
                selectedOption,
                changeVotesToPercentages(optionResults.copy(results = optionResultsWithIncrementedOption)),
                shouldAnimate
            )
        }

        override fun determineInitialState(optionIds: List<String>): VotingState {
            return if (optionIds.intersect(optionResults.results.keys).size != optionIds.size) InitialState else this
        }
    }

    data class PollAnswered(val selectedOption: String) : VotingState() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                put(TYPE, POLL_ANSWERED)
                putProp(this@PollAnswered, PollAnswered::selectedOption) { it }
            }
        }

        fun transitionToSubmittingAnswer(
            optionResults: OptionResults,
            shouldAnimate: Boolean
        ): SubmittingAnswer {
            val optionResultsWithIncrementedOption = optionResults.results.toMutableMap()
                .apply { set(selectedOption, (get(selectedOption) ?: 0) + 1) }
            return SubmittingAnswer(
                selectedOption,
                changeVotesToPercentages(optionResults.copy(results = optionResultsWithIncrementedOption)),
                shouldAnimate
            )
        }

        override fun determineInitialState(optionIds: List<String>) =
            if (!optionIds.contains(selectedOption)) InitialState else this
    }

    data class SubmittingAnswer(
        val selectedOption: String,
        val optionResults: OptionResults,
        val shouldAnimate: Boolean
    ) : VotingState() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                put(TYPE, SUBMITTING_ANSWER)
                putProp(this@SubmittingAnswer, SubmittingAnswer::selectedOption) { it }
                putProp(this@SubmittingAnswer, SubmittingAnswer::optionResults) { it.encodeJson() }
                putProp(this@SubmittingAnswer, SubmittingAnswer::shouldAnimate) { false }
            }
        }

        fun transitionToRefreshingResults(
            selectedOption: String,
            optionResults: OptionResults
        ): RefreshingResults {
            return RefreshingResults(
                selectedOption,
                optionResults,
                shouldAnimate = false,
                shouldTransition = false
            )
        }

        override fun determineInitialState(optionIds: List<String>): VotingState {
            return if ((optionIds.intersect(optionResults.results.keys).size != optionIds.size)) InitialState else this
        }
    }

    data class RefreshingResults(
        val selectedOption: String,
        val optionResults: OptionResults,
        val shouldAnimate: Boolean = true,
        val shouldTransition: Boolean = true
    ) : VotingState() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                put(TYPE, REFRESHING_RESULTS)
                putProp(this@RefreshingResults, RefreshingResults::selectedOption) { it }
                putProp(
                    this@RefreshingResults,
                    RefreshingResults::optionResults
                ) { it.encodeJson() }
            }
        }

        fun transitionToRefreshingResults(
            optionResults: OptionResults,
            shouldAnimate: Boolean = true,
            shouldTransition: Boolean = true
        ): RefreshingResults {
            return RefreshingResults(selectedOption, optionResults, shouldAnimate, shouldTransition)
        }

        override fun determineInitialState(optionIds: List<String>): VotingState {
            return if (optionIds.intersect(optionResults.results.keys).size != optionIds.size) {
                InitialState
            } else {
                copy(shouldAnimate = false, shouldTransition = true)
            }
        }
    }

    companion object {
        private const val INITIAL_STATE = "InitialState"
        private const val RESULTS_SEEDED = "ResultsSeeded"
        private const val POLL_ANSWERED = "PollAnswered"
        private const val REFRESHING_RESULTS = "RefreshingResults"
        private const val SUBMITTING_ANSWER = "SubmittingAnswer"
        private const val TYPE = "type"

        fun decodeJson(jsonObject: JSONObject): VotingState {
            return when (jsonObject.safeGetString(TYPE)) {
                RESULTS_SEEDED -> ResultsSeeded(
                    OptionResults.decodeJson(jsonObject.getJSONObject(ResultsSeeded::optionResults.name))
                )
                POLL_ANSWERED -> PollAnswered(jsonObject.getString(RefreshingResults::selectedOption.name))
                REFRESHING_RESULTS -> RefreshingResults(
                    jsonObject.getString(RefreshingResults::selectedOption.name),
                    OptionResults.decodeJson(jsonObject.getJSONObject(RefreshingResults::optionResults.name))
                )
                SUBMITTING_ANSWER -> SubmittingAnswer(
                    jsonObject.getString(SubmittingAnswer::selectedOption.name),
                    OptionResults.decodeJson(jsonObject.getJSONObject(SubmittingAnswer::optionResults.name)),
                    false
                )
                else -> InitialState
            }
        }
    }
}
