package io.rover.sdk.polls

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.rover.sdk.data.graphql.ApiResult
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.Publishers
import io.rover.sdk.ui.blocks.poll.OptionResults
import io.rover.sdk.ui.blocks.poll.VotingRepository
import io.rover.sdk.ui.blocks.poll.VotingService
import io.rover.sdk.ui.blocks.poll.VotingStorage
import org.json.JSONObject
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object VotingRepositorySpec : Spek({
    val votingService: VotingService = mock()
    val votingStorage: VotingStorage = mock()
    val optionResultsSubject: PublishSubject<OptionResults> = mock()
    val jsonObject: JSONObject = mock()

    val apiResult: ApiResult.Success<OptionResults> = mock()
    val optionResult: OptionResults = mock()
    val optionResultResponse = ""

    val votingRepository = VotingRepository(votingService, votingStorage, optionResultsSubject)
    val pollId = "poll id"
    val optionId = "option id"
    val optionIds = listOf(optionId)

    describe("cast vote") {
        it("increments saved poll state and casts vote") {
            votingRepository.castVote(pollId, optionId)
            verify(votingStorage).incrementSavedPollState(pollId, optionId)
            verify(votingService).castVote(pollId, optionId)
        }
    }

    describe("get saved poll state") {
        it("saves poll state to voting storage") {
            votingRepository.getSavedPollState(pollId)
            verify(votingStorage).getSavedPollState(pollId)
        }
    }

    describe("fetch voting results") {
        whenever(votingService.fetchResults(pollId, optionIds)).thenReturn(Publishers.just(apiResult))
        whenever(apiResult.response).thenReturn(optionResult)
        whenever(optionResult.encodeJson()).thenReturn(jsonObject)
        whenever(jsonObject.toString()).thenReturn(optionResultResponse)

        votingRepository.fetchVotingResults(pollId, optionIds)

        it("sets poll results in storage") {
            verify(votingStorage).setPollResults(pollId, optionResultResponse)
        }

        it("updates option results subject") {
            verify(optionResultsSubject).onNext(optionResult)
        }
    }
})