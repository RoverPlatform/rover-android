package io.rover.sdk.ui.blocks.poll

import io.rover.sdk.logging.log
import io.rover.sdk.platform.KeyValueStorage
import org.json.JSONException
import org.json.JSONObject
import java.lang.Exception

internal class VotingStorage(private val keyValueStorage: KeyValueStorage) {
    fun setPollResults(pollId: String, value: String) {
        keyValueStorage["$pollId-results"] = value
    }

    fun incrementSavedPollState(pollId: String, optionId: String) {
        keyValueStorage["$pollId-vote"] = optionId

        val optionResultsJson = keyValueStorage["$pollId-results"]

        optionResultsJson?.let {
            try {
                val optionResults = OptionResults.decodeJson(JSONObject(it))
                val resultsMap = optionResults.results.toMutableMap()
                resultsMap[optionId] = resultsMap[optionId]!!.plus(1)
                val resultsToInsert = optionResults.copy(results = resultsMap)

                keyValueStorage["$pollId-results"] = resultsToInsert.encodeJson().toString()
            } catch (e: JSONException) {
                log.w("Poll JSON decode problem details: $e")
                null
            } catch (e: Exception) {
                log.w("problem incrementing poll state: $e")
            }
        }
    }

    fun getSavedPollState(pollId: String): OptionResultsWithUserVote? {
        val optionResultsJson = keyValueStorage["$pollId-results"]
        val optionResultsVoteJson = keyValueStorage["$pollId-vote"]

        return optionResultsJson?.let {
            try {
                OptionResultsWithUserVote(OptionResults.decodeJson(JSONObject(optionResultsJson)), optionResultsVoteJson)
            } catch (e: JSONException) {
                log.w("Poll JSON decode problem details: $e")
                null
            }
        }
    }
}

private typealias OptionResultsWithUserVote = Pair<OptionResults, String?>