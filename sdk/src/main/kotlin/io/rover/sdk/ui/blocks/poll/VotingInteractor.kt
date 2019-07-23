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

    fun checkIfAlreadyVotedAndHaveResults(pollId: String, optionIds: List<String>, forceNetwork: Boolean = false) {
        val savedVoteState = getSavedVoteState(pollId)

        getPollState(pollId, optionIds, forceNetwork).observeOn(mainScheduler).subscribe {
            if (savedVoteState != null) {
                if (it.results.filterKeys { key -> key in optionIds }.size == optionIds.size && savedVoteState in optionIds) {
                    votingState.onNext(VotingState.Results(savedVoteState, it))
                }
            }
        }
    }

    fun castVote(pollId: String, optionId: String, optionIds: List<String>) {
        votingStorage.incrementSavedPollState(pollId, optionId)
        votingService.castVote(pollId, optionId)
        checkIfAlreadyVotedAndHaveResults(pollId, optionIds)
    }

    private fun getPollState(pollId: String, optionIds: List<String>, forceNetwork: Boolean): Publisher<OptionResults> {
        val savedState = votingStorage.getSavedPollState(pollId)
        return when {
            savedState != null && !forceNetwork -> {
                updatePollResults(pollId, optionIds)
                Publishers.just(savedState)
            }
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

    private fun updatePollResults(pollId: String, optionIds: List<String>) {
        // checkIfAlreadyVotedAndHaveResults(pollId, optionIds, true)
    }

    private fun getSavedVoteState(pollId: String) = votingStorage.getSavedVoteState(pollId)
}
