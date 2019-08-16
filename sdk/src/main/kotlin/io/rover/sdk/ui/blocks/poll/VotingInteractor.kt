package io.rover.sdk.ui.blocks.poll

import android.os.Handler
import io.rover.sdk.data.graphql.ApiResult
import io.rover.sdk.data.graphql.putProp
import io.rover.sdk.data.graphql.safeGetString
import io.rover.sdk.logging.log
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.Scheduler
import io.rover.sdk.streams.map
import io.rover.sdk.streams.observeOn
import io.rover.sdk.streams.subscribe
import org.json.JSONObject
import org.reactivestreams.Publisher

internal class VotingInteractor(
    private val votingService: VotingService,
    private val votingStorage: VotingStorage,
    private val mainScheduler: Scheduler
) {
    val votingState = PublishSubject<VotingState>()
    val refreshEvents = PublishSubject<RefreshEvent>()
    var pollId: String = ""
    var optionIds = listOf<String>()

    private var currentState: VotingState = VotingState.InitialState
    set(value) {
        field = value
        votingState.onNext(value)
        val stateToSet = if (value is VotingState.RefreshingResults) VotingState.SubmittingAnswer(value.pollId, value.selectedOption, value.optionResults, false, true) else value
        votingStorage.setLastSeenPollState(pollId, stateToSet)
        checkState(value, pollId, optionIds)
    }

    private fun checkState(votingState: VotingState, pollId: String, optionIds: List<String>) {
        transitionToState(votingState, pollId, optionIds)
    }

    private fun transitionToState(votingState: VotingState, pollId: String, optionIds: List<String>) {
        when (votingState) {
            is VotingState.InitialState -> initialState(pollId, optionIds)
            is VotingState.ResultsSeeded -> {}
            is VotingState.PollAnswered -> {
                pollAnswered(pollId, votingState.selectedOption, optionIds)
            }
            is VotingState.SubmittingAnswer -> {
                if (optionIds.intersect(votingState.optionResults.results.keys).size != optionIds.size) {
                    currentState = VotingState.InitialState
                } else {
                    submittingAnswer(votingState)
                }
            }
        }
    }

    fun initialize(pollId: String, optionIds: List<String>) {
        this.pollId = pollId
        this.optionIds = optionIds

        when (val state = votingStorage.getLastSeenPollState(pollId)) {
            is VotingState.SubmittingAnswer -> {
                if (optionIds.intersect(state.optionResults.results.keys).size != optionIds.size) {
                    currentState = VotingState.InitialState
                } else {
                    currentState = votingStorage.getLastSeenPollState(pollId)
                }
            }
            is VotingState.ResultsSeeded -> {
                if (optionIds.intersect(state.optionResults.results.keys).size != optionIds.size) {
                    currentState = VotingState.InitialState
                }
            }
            is VotingState.PollAnswered -> {
                if (!optionIds.contains(state.selectedOption)){
                    currentState = votingStorage.getLastSeenPollState(pollId)
                } else {
                    pollAnswered(pollId, state.selectedOption, optionIds)
                }
            }
            else -> currentState = votingStorage.getLastSeenPollState(pollId)
        }

    }

    private fun initialState(pollId: String, optionIds: List<String>) {
        getPollStateFromNetwork(pollId, optionIds).observeOn(mainScheduler).subscribe { optionResults ->
            val resultsSameKeysAsShown = optionResults.results.filterKeys { key -> key in optionIds }.size == optionIds.size

            if (resultsSameKeysAsShown && optionResults.results.isNotEmpty()) currentState = VotingState.ResultsSeeded(optionResults)
        }
    }

    fun castVotes(pollId: String, optionId: String, optionIds: List<String>) {
        when (val current = currentState) {
            is VotingState.ResultsSeeded -> {
                val optionWithIncrementedVote = current.optionResults.results.toMutableMap().apply {
                    set(optionId, (get(optionId) ?: 0) + 1)
                }
                currentState = VotingState.SubmittingAnswer(pollId, optionId, changeVotesToPercentages(current.optionResults.copy(results = optionWithIncrementedVote)), true, false)
            }
            is VotingState.InitialState -> currentState = VotingState.PollAnswered(optionId)
        }
    }

    fun submittingAnswer(submittingAnswer: VotingState.SubmittingAnswer) {
        submittingAnswerHandler.removeCallbacksAndMessages(null)

        if (submittingAnswer.answerSubmitted) {
            currentState = VotingState.RefreshingResults(submittingAnswer.pollId, submittingAnswer.selectedOption, submittingAnswer.optionResults)
        } else {
            votingService.castVote(submittingAnswer.pollId, submittingAnswer.selectedOption).observeOn(mainScheduler).subscribe ({ voteOutcome ->
                    if (voteOutcome is VoteOutcome.VoteSuccess) {
                        submittingAnswerHandler.removeCallbacksAndMessages(null)
                        currentState = VotingState.RefreshingResults(submittingAnswer.pollId, submittingAnswer.selectedOption, submittingAnswer.optionResults)
                    } else {
                        createVoteSenderBackoff(submittingAnswer)
                    }
            }, { _ -> createVoteSenderBackoff(submittingAnswer)})
        }
    }

    private val submittingAnswerHandler = Handler()
    private var submittingAnswerTimesInvoked = 0

    private fun createVoteSenderBackoff(submittingAnswer: VotingState.SubmittingAnswer) {
        submittingAnswerHandler.removeCallbacksAndMessages(null)
        val runnableCode = object : Runnable {
            override fun run() {
                try {
                    log.v("vote sent")
                    submittingAnswerTimesInvoked++
                    submittingAnswer(submittingAnswer)
                } catch (e: Exception) {
                    log.w("issue trying to send vote results $e")
                }
            }
        }

        submittingAnswerHandler.postDelayed(runnableCode, 2000L * submittingAnswerTimesInvoked)
    }

    fun pollAnswered(pollId: String, voteOptionId: String, optionIds: List<String>) {
        pollAnsweredHandler.removeCallbacksAndMessages(null)

        getPollStateFromNetwork(pollId, optionIds).observeOn(mainScheduler).subscribe ({ optionResults ->
            val resultsSameKeysAsShown = optionResults.results.filterKeys { key -> key in optionIds }.size == optionIds.size

            if (resultsSameKeysAsShown && optionResults.results.isNotEmpty()) {
                pollAnsweredHandler.removeCallbacksAndMessages(null)

                val optionWithIncrementedVote = optionResults.results.toMutableMap().apply {
                    set(voteOptionId, (get(voteOptionId) ?: 0) + 1)
                }

                currentState = VotingState.SubmittingAnswer(pollId, voteOptionId, changeVotesToPercentages(optionResults.copy(results = optionWithIncrementedVote)), true, false)
            } else {
                createRetrieveResultsBackoff(pollId, voteOptionId, optionIds)
            }
        }, { _ -> createRetrieveResultsBackoff(pollId, voteOptionId, optionIds) })
    }

    private val pollAnsweredHandler = Handler()
    private var timesInvoked: Int = 0

    private fun createRetrieveResultsBackoff(pollId: String, voteOptionId: String, optionIds: List<String>) {
        pollAnsweredHandler.removeCallbacksAndMessages(null)
        val runnableCode = object : Runnable {
            override fun run() {
                try {
                    timesInvoked++
                    pollAnswered(pollId, voteOptionId, optionIds)
                } catch (e: Exception) {
                    log.w("issue trying to retrieve results $e")
                }
            }
        }

        pollAnsweredHandler.postDelayed(runnableCode, 2000L * timesInvoked)
    }

    // the strange refreshing state with the refresh events and the back and forth state is a hangover from the previous implementation
    // due to the UI being unable to transition straight to the Refreshing state, this should be changed in the future
    fun votingResultsUpdate(pollId: String, optionIds: List<String>) {
        getPollStateFromNetwork(pollId, optionIds).observeOn(mainScheduler).subscribe { fetchedOptionResults ->
            val resultsSameKeysAsShown = fetchedOptionResults.results.filterKeys { key -> key in optionIds }.size == optionIds.size

            when (val state = currentState) {
                is VotingState.RefreshingResults -> if (fetchedOptionResults.results.isNotEmpty() && resultsSameKeysAsShown && pollId == this.pollId){
                    currentState = state.copy(optionResults = changeVotesToPercentages(fetchedOptionResults))
                    refreshEvents.onNext(RefreshEvent(pollId, changeVotesToPercentages(fetchedOptionResults)))
                }}
            }
    }

    private fun getPollStateFromNetwork(pollId: String, optionIds: List<String>): Publisher<OptionResults> {
        return fetchVotingResults(pollId, optionIds)
    }

    private fun fetchVotingResults(pollId: String, optionIds: List<String>): Publisher<OptionResults> {
        return votingService.fetchResults(pollId, optionIds).map {
            if (it is ApiResult.Success<OptionResults>) {
                it.response
            } else {
                OptionResults(mapOf())
            }
        }
    }

    private fun changeVotesToPercentages(results: OptionResults): OptionResults {
        // https://en.wikipedia.org/wiki/Largest_remainder_method
        val total = results.results.values.sum().toFloat()
        val votes = results.results.mapValues { (it.value.toFloat() / total * 100) }

        val votesWithFractional = votes.toList().map { VotesWithFractional(it.first, it.second.toInt(), it.second - it.second.toInt()) }

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

    data class VotesWithFractional(val key: String, var votePercentage: Int, val fractional: Float)
}

internal sealed class VotingState {
    abstract fun encodeJson(): JSONObject

    object InitialState : VotingState() {
    override fun encodeJson(): JSONObject {
        return JSONObject().apply {
            put(TYPE, INITIAL_STATE)
        }
    }
    }
    data class ResultsSeeded(val optionResults: OptionResults) : VotingState() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                put(TYPE, RESULTS_SEEDED)
                putProp(this@ResultsSeeded, ResultsSeeded::optionResults) { it.encodeJson() }
            }
        }
    }
    data class PollAnswered(val selectedOption: String) : VotingState() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                put(TYPE, POLL_ANSWERED)
                putProp(this@PollAnswered, PollAnswered::selectedOption) { it }
            }
        }
    }
    data class RefreshingResults(val pollId: String, val selectedOption: String, val optionResults: OptionResults) : VotingState() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply { 
                put(TYPE, REFRESHING_RESULTS)
                putProp(this@RefreshingResults, RefreshingResults::pollId) { it }
                putProp(this@RefreshingResults, RefreshingResults::selectedOption) { it }
                putProp(this@RefreshingResults, RefreshingResults::optionResults) { it.encodeJson() }
            }
        }
    }
    data class SubmittingAnswer(val pollId: String, val selectedOption: String, val optionResults: OptionResults, val shouldAnimate: Boolean, val answerSubmitted: Boolean) : VotingState() {
    override fun encodeJson(): JSONObject {
        return JSONObject().apply {
            put(TYPE, SUBMITTING_ANSWER)
            putProp(this@SubmittingAnswer, SubmittingAnswer::pollId) { it }
            putProp(this@SubmittingAnswer, SubmittingAnswer::selectedOption) { it }
            putProp(this@SubmittingAnswer, SubmittingAnswer::optionResults) { it.encodeJson() }
            putProp(this@SubmittingAnswer, SubmittingAnswer::shouldAnimate) { false }
            putProp(this@SubmittingAnswer, SubmittingAnswer::answerSubmitted) { it }
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
                    jsonObject.getString(RefreshingResults::pollId.name),
                    jsonObject.getString(RefreshingResults::selectedOption.name),
                    OptionResults.decodeJson(jsonObject.getJSONObject(RefreshingResults::optionResults.name))
                )
                SUBMITTING_ANSWER -> SubmittingAnswer(
                    jsonObject.getString(SubmittingAnswer::pollId.name),
                    jsonObject.getString(SubmittingAnswer::selectedOption.name),
                    OptionResults.decodeJson(jsonObject.getJSONObject(SubmittingAnswer::optionResults.name)),
                    false,
                jsonObject.getBoolean(SubmittingAnswer::answerSubmitted.name))
                else -> InitialState
            }
        }
    }
}

internal data class RefreshEvent(val pollId: String, val optionResults: OptionResults)
