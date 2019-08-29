package io.rover.sdk.ui.blocks.poll

import android.os.Handler
import io.rover.sdk.data.graphql.ApiResult
import io.rover.sdk.data.graphql.putProp
import io.rover.sdk.data.graphql.safeGetString
import io.rover.sdk.logging.log
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.Scheduler
import io.rover.sdk.streams.first
import io.rover.sdk.streams.map
import io.rover.sdk.streams.observeOn
import io.rover.sdk.streams.subscribe
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
            is VotingState.InitialState -> initialState(pollId, optionIds)
            is VotingState.ResultsSeeded, is VotingState.PollAnswered -> {}
            is VotingState.SubmittingAnswer -> submittingAnswer(value)
            is VotingState.RefreshingResults -> refreshingResults()
        }
    }

    private var cancelled = true

    fun cancel() {
        refreshingResultsHandler.removeCallbacksAndMessages(null)
        resultsRetrievalHandler.removeCallbacksAndMessages(null)
        submittingAnswerHandler.removeCallbacksAndMessages(null)
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

        cancelled = false

        when (val state = votingStorage.getLastSeenPollState(pollId)) {
            is VotingState.ResultsSeeded -> {
                if (optionIds.intersect(state.optionResults.results.keys).size != optionIds.size) {
                    currentState = VotingState.InitialState
                }
            }
            is VotingState.PollAnswered -> {
                if (!optionIds.contains(state.selectedOption)){
                    currentState = VotingState.InitialState
                } else {
                    currentState = votingStorage.getLastSeenPollState(pollId)
                    initialState(pollId, optionIds)
                }
            }
            is VotingState.SubmittingAnswer -> {
                currentState = if (optionIds.intersect(state.optionResults.results.keys).size != optionIds.size) {
                    VotingState.InitialState
                } else {
                    votingStorage.getLastSeenPollState(pollId)
                }
            }
            is VotingState.RefreshingResults -> {
                currentState = if (optionIds.intersect(state.optionResults.results.keys).size != optionIds.size) {
                     VotingState.InitialState
                } else {
                    state.copy(shouldAnimate = false, shouldTransition = true)
                }
            }
            else -> currentState = votingStorage.getLastSeenPollState(pollId)
        }
    }

    private fun initialState(pollId: String, optionIds: List<String>) {
        resultsRetrievalHandler.removeCallbacksAndMessages(null)

        fetchVotingResults(optionIds).observeOn(mainScheduler).first().subscribe ({ optionResults ->
            if (optionResults.results.isNotEmpty()) {
            when (val state = currentState) {
                is VotingState.PollAnswered -> currentState = state.transitionToSubmittingAnswer(optionResults, true)
                is VotingState.InitialState -> currentState = state.transitionToResultsSeeded(optionResults)
            }}
            else { createRetrieveResultsBackoff(pollId, optionIds) }
        }, { createRetrieveResultsBackoff(pollId, optionIds) }, { subscriptions.add(it) })
    }

    fun castVotes(optionId: String) {
        when (val state = currentState) {
            is VotingState.ResultsSeeded -> currentState = state.transitionToSubmittingAnswer(optionId, true)
            is VotingState.InitialState -> currentState = state.transitionToPollAnswered(optionId)
        }
    }

    private fun submittingAnswer(submittingAnswer: VotingState.SubmittingAnswer) {
        submittingAnswerHandler.removeCallbacksAndMessages(null)

            votingService.castVote(pollId, submittingAnswer.selectedOption).observeOn(mainScheduler).first().subscribe ({ voteOutcome ->
                val state = currentState
                if (voteOutcome is VoteOutcome.VoteSuccess) {
                    if (state is VotingState.SubmittingAnswer) currentState = state.transitionToRefreshingResults(state.selectedOption, state.optionResults)
                } else {
                    createVoteSenderBackoff(submittingAnswer)
                }
            }, { createVoteSenderBackoff(submittingAnswer)}, {subscriptions.add(it)})
    }

    private fun votingResultsUpdate(optionIds: List<String>) {
        refreshingResultsHandler.removeCallbacksAndMessages(null)

        fetchVotingResults(optionIds).observeOn(mainScheduler).first().subscribe ({ fetchedOptionResults ->
            val state = currentState
            if (state is VotingState.RefreshingResults && fetchedOptionResults.results.isNotEmpty()) {
                currentState = state.transitionToRefreshingResults(changeVotesToPercentages(fetchedOptionResults), shouldTransition = true, shouldAnimate = true)
            }
        }, { if(currentState is VotingState.RefreshingResults) refreshingResults() }, { subscription ->
            currentUpdateSubscription?.cancel()
            currentUpdateSubscription = subscription
        })
    }

    private fun fetchVotingResults(optionIds: List<String>): Publisher<OptionResults> {
        return votingService.fetchResults(pollId, optionIds).map {
            if (it is ApiResult.Success<OptionResults>) it.response else OptionResults(mapOf())
        }
    }

    private val resultsRetrievalHandler = Handler()
    private var resultsRetrievalTimesInvoked: Int = 0

    private val submittingAnswerHandler = Handler()
    private var submittingAnswerTimesInvoked = 0

    private var currentUpdateSubscription: Subscription? = null
    private val refreshingResultsHandler = Handler()

    private fun createVoteSenderBackoff(submittingAnswer: VotingState.SubmittingAnswer) {
        submittingAnswerHandler.removeCallbacksAndMessages(null)
        submittingAnswerHandler.postDelayed({
            try {
                log.v("vote sent")
                submittingAnswerTimesInvoked++
                submittingAnswer(submittingAnswer)
            } catch (e: Exception) {
                log.w("issue trying to send vote results $e")
            }
        }, VOTE_SUBMISSION_BACKOFF_DELAY * submittingAnswerTimesInvoked)
    }

    private fun createRetrieveResultsBackoff(pollId: String, optionIds: List<String>) {
        resultsRetrievalHandler.removeCallbacksAndMessages(null)
        resultsRetrievalHandler.postDelayed({
            try {
                resultsRetrievalTimesInvoked++
                initialState(pollId, optionIds)
            } catch (e: Exception) {
                log.w("issue trying to retrieve results $e")
            }
        }, RESULTS_RETRIEVAL_BACKOFF_DELAY * resultsRetrievalTimesInvoked)
    }

    private fun refreshingResults() {
        refreshingResultsHandler.removeCallbacksAndMessages(null)
        refreshingResultsHandler.postDelayed({
            try {
                votingResultsUpdate(optionIds)
            } catch (e: Exception) {
                log.w("issue trying to send vote results $e")
            }
        }, REFRESHING_RESULTS_DELAY)
    }


}

private data class VotesWithFractional(val key: String, var votePercentage: Int, val fractional: Float)

private fun changeVotesToPercentages(results: OptionResults): OptionResults {
    // https://en.wikipedia.org/wiki/Largest_remainder_method
    val total = results.results.values.sum().toFloat()
    val votes = results.results.mapValues { (it.value.toFloat() / total * 100) }

    val votesWithFractional = votes.toList().map {
        VotesWithFractional(
            it.first,
            it.second.toInt(),
            it.second - it.second.toInt()
        )
    }

    var differenceBetweenVotesAndTotalPercentage = 100 - votesWithFractional.sumBy { it.votePercentage }

    val votesList = votesWithFractional.sortedByDescending { it.fractional }.toMutableList()
    val maxIndex = votesList.size - 1
    var currentIndex = 0

    while (differenceBetweenVotesAndTotalPercentage > 0) {
        votesList[currentIndex].votePercentage++
        if (currentIndex == maxIndex) currentIndex = 0 else currentIndex++
        differenceBetweenVotesAndTotalPercentage--
    }

    val votesListWithRemainderShared = votesList.associate { it.key to it.votePercentage }

    return results.copy(results = votesListWithRemainderShared)
}

internal sealed class VotingState {
    abstract fun encodeJson(): JSONObject

    object InitialState : VotingState() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                put(TYPE, INITIAL_STATE)
            }
        }
        fun transitionToResultsSeeded(results: OptionResults) = ResultsSeeded(results)
        fun transitionToPollAnswered(selectedOption: String) = PollAnswered(selectedOption)
    }
    data class ResultsSeeded(val optionResults: OptionResults) : VotingState() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                put(TYPE, RESULTS_SEEDED)
                putProp(this@ResultsSeeded, ResultsSeeded::optionResults) { it.encodeJson() }
            }
        }

        fun transitionToSubmittingAnswer(selectedOption: String, shouldAnimate: Boolean): SubmittingAnswer {
            val optionResultsWithIncrementedOption = optionResults.results.toMutableMap().apply { set(selectedOption, (get(selectedOption) ?: 0) + 1) }
            return SubmittingAnswer(selectedOption, changeVotesToPercentages(optionResults.copy(results = optionResultsWithIncrementedOption)), shouldAnimate)
        }
    }
    data class PollAnswered(val selectedOption: String) : VotingState() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                put(TYPE, POLL_ANSWERED)
                putProp(this@PollAnswered, PollAnswered::selectedOption) { it }
            }
        }

        fun transitionToSubmittingAnswer(optionResults: OptionResults, shouldAnimate: Boolean): SubmittingAnswer {
            val optionResultsWithIncrementedOption = optionResults.results.toMutableMap().apply { set(selectedOption, (get(selectedOption) ?: 0) + 1) }
            return SubmittingAnswer(selectedOption, changeVotesToPercentages(optionResults.copy(results = optionResultsWithIncrementedOption)), shouldAnimate)
        }
    }
    data class SubmittingAnswer(val selectedOption: String, val optionResults: OptionResults, val shouldAnimate: Boolean) : VotingState() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                put(TYPE, SUBMITTING_ANSWER)
                putProp(this@SubmittingAnswer, SubmittingAnswer::selectedOption) { it }
                putProp(this@SubmittingAnswer, SubmittingAnswer::optionResults) { it.encodeJson() }
                putProp(this@SubmittingAnswer, SubmittingAnswer::shouldAnimate) { false }
            }
        }
        fun transitionToRefreshingResults(selectedOption: String, optionResults: OptionResults): RefreshingResults {
            return RefreshingResults(selectedOption, optionResults, shouldAnimate = false, shouldTransition = false)
        }
    }
    data class RefreshingResults(val selectedOption: String, val optionResults: OptionResults, val shouldAnimate: Boolean = true, val shouldTransition: Boolean = true) : VotingState() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                put(TYPE, REFRESHING_RESULTS)
                putProp(this@RefreshingResults, RefreshingResults::selectedOption) { it }
                putProp(this@RefreshingResults, RefreshingResults::optionResults) { it.encodeJson() }
            }
        }
        fun transitionToRefreshingResults(optionResults: OptionResults, shouldAnimate: Boolean = true, shouldTransition: Boolean = true): RefreshingResults {
            return RefreshingResults(selectedOption, optionResults, shouldAnimate, shouldTransition)
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
                    false)
                else -> InitialState
            }
        }
    }
}
