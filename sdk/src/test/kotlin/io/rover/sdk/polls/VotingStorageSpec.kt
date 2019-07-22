package io.rover.sdk.polls

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.rover.sdk.platform.KeyValueStorage
import io.rover.sdk.ui.blocks.poll.VotingStorage
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object VotingStorageSpec : Spek({
    val keyValueStorage: KeyValueStorage = mock()
    val votingStorage = VotingStorage(keyValueStorage)

    describe("set poll results") {
        val pollId = "pollId"
        val expectedKey = "pollId-results"
        val value = "value"

        it("sets correct poll results key to value in keyValueStorage") {
            votingStorage.setPollResults(pollId, value)
            verify(keyValueStorage)[expectedKey] = value
        }
    }
})