package io.rover.sdk.ui.blocks.poll

import io.rover.sdk.data.graphql.ApiResult
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.subscribe

internal class VotingRepository(
    private val votingService: VotingService,
    private val votingStorage: VotingStorage) {

    //TODO: ensure when listening only list for desired poll id
    val optionResults: PublishSubject<OptionResults> = PublishSubject()

    fun fetchVotingResults(pollId: String, optionIds: List<String>) {
        votingService.fetchResults(pollId, optionIds).subscribe {
            if(it is ApiResult.Success<OptionResults>) {
                it.response.encodeJson().toString()
                votingStorage.setPollResults(pollId, it.response.encodeJson().toString())
                optionResults.onNext(it.response)
            }
        }
    }

    fun castVote(pollId: String, optionId: String) {
        votingStorage.incrementSavedPollState(pollId, optionId)
        votingService.castVote(pollId, optionId)
    }

    fun getSavedPollState(pollId: String) = votingStorage.getSavedPollState(pollId)
}