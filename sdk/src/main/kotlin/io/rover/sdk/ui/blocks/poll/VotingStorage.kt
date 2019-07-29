package io.rover.sdk.ui.blocks.poll

import io.rover.sdk.logging.log
import io.rover.sdk.platform.KeyValueStorage
import org.json.JSONException
import org.json.JSONObject
import java.lang.Exception

internal class VotingStorage(private val keyValueStorage: KeyValueStorage) {
    companion object {
        private const val MAX_SIZE = 100
        private const val ITEM_NUMBER_KEY = "item-number"
    }

    fun setPollResults(pollId: String, value: String) {
        keyValueStorage["$pollId-results"] = value
    }

    fun incrementSavedPollState(pollId: String, optionId: String) {
        keyValueStorage["$pollId-vote"] = optionId

        addItemToPrefsQueue(pollId)
        deleteOldestIfOverLimit()

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
            } catch (e: Exception) {
                log.w("problem incrementing poll state: $e")
            }
        }
    }

    fun getSavedVoteState(pollId: String) = keyValueStorage["$pollId-vote"]

    fun getSavedPollState(pollId: String): OptionResults? {
        val optionResultsJson = keyValueStorage["$pollId-results"]

        return optionResultsJson?.let {
            try {
                OptionResults.decodeJson(JSONObject(optionResultsJson))
            } catch (e: JSONException) {
                log.w("Poll JSON decode problem details: $e")
                null
            }
        }
    }

    private fun addItemToPrefsQueue(pollId: String) {
        keyValueStorage["${getCurrentCount()}"] = pollId
        keyValueStorage[ITEM_NUMBER_KEY] = keyValueStorage.getInt(ITEM_NUMBER_KEY).inc()
    }

    private fun getCurrentCount() = keyValueStorage.getInt(ITEM_NUMBER_KEY)

    private fun deleteOldestIfOverLimit() {
        val currentCount = getCurrentCount()

        if (currentCount >= MAX_SIZE) {
            val itemCountToDelete = "${currentCount - MAX_SIZE}"
            val itemToDelete = keyValueStorage[itemCountToDelete]

            itemToDelete?.let {
                keyValueStorage.unset(itemCountToDelete)
                keyValueStorage.unset("$itemToDelete-results")
                keyValueStorage.unset("$itemToDelete-vote")
            }
        }
    }
}
