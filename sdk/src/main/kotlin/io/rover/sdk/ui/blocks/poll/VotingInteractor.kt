package io.rover.sdk.ui.blocks.poll

import io.rover.sdk.data.graphql.ApiResult
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.Publishers
import io.rover.sdk.streams.Scheduler
import io.rover.sdk.streams.map
import io.rover.sdk.streams.observeOn
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.blocks.poll.text.VotingState
import org.reactivestreams.Publisher
import java.util.Timer
import java.util.TimerTask

internal class VotingInteractor(
    private val votingService: VotingService,
    private val votingStorage: VotingStorage,
    private val mainScheduler: Scheduler
) {
    val votingState = PublishSubject<VotingState>()

    fun checkIfAlreadyVotedAndHaveResults(pollId: String, optionIds: List<String>) {
        getPollState(pollId, optionIds).observeOn(mainScheduler).subscribe { optionResults ->
            doIfCanShowResultsState(votingStorage.getSavedVoteState(pollId), optionIds, optionResults) { vote ->
                votingState.onNext(VotingState.Results(vote, changeVotesToPercentages(optionResults), false))
            }
        }
    }

    private fun getFirstTimeResults(pollId: String, optionIds: List<String>) {
        getPollState(pollId, optionIds).observeOn(mainScheduler).subscribe { optionResults ->
            doIfCanShowResultsState(votingStorage.getSavedVoteState(pollId), optionIds, optionResults) { vote ->
                votingState.onNext(VotingState.Results(vote, changeVotesToPercentages(optionResults), true))
            }
        }
    }

    private fun votingResultsUpdate(pollId: String, optionIds: List<String>) {
        getPollStateFromNetwork(pollId, optionIds).observeOn(mainScheduler).subscribe { optionResults ->
            doIfCanShowResultsState(votingStorage.getSavedVoteState(pollId), optionIds, optionResults) {
                votingState.onNext(VotingState.Update(changeVotesToPercentages(optionResults)))
            }
        }
    }

    fun castVote(pollId: String, optionId: String, optionIds: List<String>) {
        votingStorage.incrementSavedPollState(pollId, optionId)
        votingService.castVote(pollId, optionId)

        getFirstTimeResults(pollId, optionIds)
    }

    private fun doIfCanShowResultsState(savedVoteState: String?, optionIds: List<String>, optionResults: OptionResults,
        onComplete: (String) -> Unit) {
        val resultsSameKeysAsShown = optionResults.results.filterKeys { key -> key in optionIds }.size == optionIds.size

        if (savedVoteState != null && resultsSameKeysAsShown && savedVoteState in optionIds) {
            onComplete(savedVoteState)
        }
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

    private fun changeVotesToPercentages(results: OptionResults): OptionResults {
        // https://en.wikipedia.org/wiki/Largest_remainder_method
        val total = results.results.values.sum().toFloat()
        val votes = results.results.mapValues { (it.value.toFloat() / total * 100)  }

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
