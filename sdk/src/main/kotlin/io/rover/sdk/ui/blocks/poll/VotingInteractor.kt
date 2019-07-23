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

internal class VotingInteractor(
    private val votingService: VotingService,
    private val votingStorage: VotingStorage,
    private val mainScheduler: Scheduler
) {
    val votingState = PublishSubject<VotingState>()

    fun checkIfAlreadyVotedAndHaveResults(pollId: String, optionIds: List<String>, update: Boolean = true) {
        val savedVoteState = votingStorage.getSavedVoteState(pollId)

        getPollState(pollId, optionIds, false).observeOn(mainScheduler).subscribe {
            val resultsSameKeysAsShown = it.results.filterKeys { key -> key in optionIds }.size == optionIds.size
            if (savedVoteState != null && resultsSameKeysAsShown && savedVoteState in optionIds) {
                votingState.onNext(VotingState.Results(savedVoteState, changeVotesToPercentages(it)))
                if (update) votingResultsUpdate(pollId, optionIds)
            }
        }
    }

    private fun votingResultsUpdate(pollId: String, optionIds: List<String>) {
        val savedVoteState = votingStorage.getSavedVoteState(pollId)

        getPollState(pollId, optionIds, true).observeOn(mainScheduler).subscribe {
            val resultsSameKeysAsShown = it.results.filterKeys { key -> key in optionIds }.size == optionIds.size
            if (savedVoteState != null && resultsSameKeysAsShown && savedVoteState in optionIds) {
                votingState.onNext(VotingState.Update(changeVotesToPercentages(it)))
            }
        }
    }

    fun castVote(pollId: String, optionId: String, optionIds: List<String>) {
        votingStorage.incrementSavedPollState(pollId, optionId)
        votingService.castVote(pollId, optionId)

        // Don't want to update immediately after casting vote
        checkIfAlreadyVotedAndHaveResults(pollId, optionIds, false)
    }

    private fun getPollState(pollId: String, optionIds: List<String>, forceNetwork: Boolean): Publisher<OptionResults> {
        val savedState = votingStorage.getSavedPollState(pollId)
        return when {
            savedState != null && !forceNetwork -> Publishers.just(savedState)
            else -> fetchVotingResults(pollId, optionIds)
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
