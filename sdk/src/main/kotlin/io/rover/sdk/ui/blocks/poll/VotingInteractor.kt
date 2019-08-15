package io.rover.sdk.ui.blocks.poll

import android.os.Handler
import io.rover.sdk.data.graphql.ApiResult
import io.rover.sdk.logging.log
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.Publishers
import io.rover.sdk.streams.Scheduler
import io.rover.sdk.streams.map
import io.rover.sdk.streams.observeOn
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.blocks.poll.text.VotingState
import org.reactivestreams.Publisher

internal class VotingInteractor(
    private val votingService: VotingService,
    private val votingStorage: VotingStorage,
    private val mainScheduler: Scheduler
) {
    val votingState = PublishSubject<VotingState>()

    fun checkIfAlreadyVotedAndHaveResults(pollId: String, optionIds: List<String>) {
        if(votingStorage.retrieveIfLastSeenPollStatePollAnswered(pollId)
            && votingStorage.getSavedVoteState(pollId) != null
            && votingStorage.getSavedVoteState(pollId) ?: "" in optionIds) {
            castVote(pollId, votingStorage.getSavedVoteState(pollId)!!, optionIds)
        } else {
            getPollState(pollId, optionIds).observeOn(mainScheduler).subscribe { optionResults ->
                val resultsSameKeysAsShown = optionResults.results.filterKeys { key -> key in optionIds }.size == optionIds.size
                val savedVoteState = votingStorage.getSavedVoteState(pollId)

                if (savedVoteState != null && resultsSameKeysAsShown && savedVoteState in optionIds) {
                    votingState.onNext(VotingState.Results(pollId, savedVoteState, changeVotesToPercentages(optionResults, savedVoteState), false))
                }
            }
        }
    }

    private fun getFirstTimeResults(pollId: String, voteOptionId: String, optionIds: List<String>) {
        handler.removeCallbacksAndMessages(null)
        val savedState = votingStorage.getSavedPollState(pollId)

        votingState.onNext(VotingState.PollAnswered)
        votingStorage.setLastSeenPollStatePollAnswered(pollId)

        val retrievePollState = if (savedState != null && savedState.results.filterKeys { key -> key in optionIds }.size == optionIds.size) {
            Publishers.just(savedState)
        } else {
            getPollStateFromNetwork(pollId, optionIds)
        }

        retrievePollState.observeOn(mainScheduler).subscribe ({ optionResults ->
            val resultsSameKeysAsShown = optionResults.results.filterKeys { key -> key in optionIds }.size == optionIds.size

            votingStorage.incrementSavedPollState(pollId, voteOptionId)
            votingService.castVote(pollId, voteOptionId)
            val savedVoteState = votingStorage.getSavedVoteState(pollId)
            val savedPollState = votingStorage.getSavedPollState(pollId)

            if (savedVoteState != null && resultsSameKeysAsShown && savedVoteState in optionIds && savedPollState != null) {
                handler.removeCallbacksAndMessages(null)
                votingStorage.setLastSeenPollStateEmpty(pollId)
                votingState.onNext(VotingState.Results(pollId, savedVoteState, changeVotesToPercentages(savedPollState, savedVoteState), true))
            } else {
                createExponentialResultsBackoffHandler(pollId, voteOptionId, optionIds)
            }
        }, { _ -> createExponentialResultsBackoffHandler(pollId, voteOptionId, optionIds) })
    }

    private val handler = Handler()
    private var timesInvoked: Int = 0

    private fun createExponentialResultsBackoffHandler(pollId: String, voteOptionId: String, optionIds: List<String>) {
        handler.removeCallbacksAndMessages(null)
        val runnableCode = object : Runnable {
            override fun run() {
                log.v("tried to get results")
                timesInvoked++
                getFirstTimeResults(pollId, voteOptionId, optionIds)
            }
        }

        handler.postDelayed(runnableCode, 2000L * timesInvoked)
    }
    
    fun votingResultsUpdate(pollId: String, optionIds: List<String>) {
        getPollStateFromNetwork(pollId, optionIds).observeOn(mainScheduler).subscribe { optionResults ->
            val resultsSameKeysAsShown = optionResults.results.filterKeys { key -> key in optionIds }.size == optionIds.size
            val savedVoteState = votingStorage.getSavedVoteState(pollId)

            if (savedVoteState != null && resultsSameKeysAsShown && savedVoteState in optionIds) {
                votingState.onNext(VotingState.Update(changeVotesToPercentages(optionResults, savedVoteState)))
            }
        }
    }

    fun castVote(pollId: String, optionId: String, optionIds: List<String>) {
        createExponentialResultsBackoffHandler(pollId, optionId, optionIds)
    }

    private fun getPollStateFromNetwork(pollId: String, optionIds: List<String>): Publisher<OptionResults> {
        return fetchVotingResults(pollId, optionIds)
    }

    private fun getSavedPollState(pollId: String): Publisher<OptionResults> {
        val savedState = votingStorage.getSavedPollState(pollId)
        return savedState?.let { Publishers.just(savedState) } ?: Publishers.just(OptionResults(mapOf()))
    }

    private fun getPollState(pollId: String, optionIds: List<String>): Publisher<OptionResults> {
        val savedState = votingStorage.getSavedPollState(pollId)
        return when {
            savedState != null -> getSavedPollState(pollId)
            else -> getPollStateFromNetwork(pollId, optionIds)
        }
    }

    private fun fetchVotingResults(pollId: String, optionIds: List<String>): Publisher<OptionResults> {
        return votingService.fetchResults(pollId, optionIds).map {
            if (it is ApiResult.Success<OptionResults>) {
                votingStorage.setPollResults(pollId, it.response.encodeJson().toString())
                it.response
            } else {
                OptionResults(mapOf())
            }
        }
    }

    private fun changeVotesToPercentages(results: OptionResults, savedVoteState: String): OptionResults {

        val modifiedResults = if (results.results[savedVoteState] == 0) {
            val resultsMap = results.results.toMutableMap().apply { this[savedVoteState] = 1 }
            results.copy(results = resultsMap)
        } else {
            results
        }

        // https://en.wikipedia.org/wiki/Largest_remainder_method
        val total = modifiedResults.results.values.sum().toFloat()
        val votes = modifiedResults.results.mapValues { (it.value.toFloat() / total * 100) }

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

        return modifiedResults.copy(results = votesListWithRemainderShared)
    }

    data class VotesWithFractional(val key: String, var votePercentage: Int, val fractional: Float)
}
