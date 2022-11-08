package io.rover.sdk.experiences.ui.blocks.poll

import io.rover.sdk.experiences.logging.log
import io.rover.sdk.experiences.platform.KeyValueStorage
import org.json.JSONException
import org.json.JSONObject
import java.lang.Exception

internal class VotingStorage(private val keyValueStorage: KeyValueStorage) {
    companion object {
        private const val MAX_SIZE = 100
        private const val ITEM_NUMBER_KEY = "item-number"
    }
    
    fun setLastSeenPollState(pollId: String, pollState: VotingState) {
        try {
            addItemToPrefsQueue(pollId)
            deleteOldestIfOverLimit()
            keyValueStorage["$pollId-lastState"] = pollState.encodeJson().toString()
        } catch (e: JSONException) {
            log.w("Poll JSON decode problem details: $e")
        } catch (e: Exception) {
            log.w("problem incrementing poll state: $e")
        }
    }

    fun getLastSeenPollState(pollId: String): VotingState {
        val json = keyValueStorage["$pollId-lastState"]

        return json?.let {
            try {
                VotingState.decodeJson(JSONObject(it))
            } catch (e: JSONException) {
                log.w("Poll JSON state decode problem details: $e")
                VotingState.InitialState
            }
        } ?: VotingState.InitialState
    }

    private fun addItemToPrefsQueue(pollId: String) {
        if (keyValueStorage["$pollId-lastState"] == null) {
            keyValueStorage["${keyValueStorage.getInt(ITEM_NUMBER_KEY)}"] = pollId
            keyValueStorage[ITEM_NUMBER_KEY] = keyValueStorage.getInt(ITEM_NUMBER_KEY).inc()
        }
    }

    private fun deleteOldestIfOverLimit() {
        val currentCount = keyValueStorage.getInt(ITEM_NUMBER_KEY)

        if (currentCount >= MAX_SIZE) {
            val itemCountToDelete = "${currentCount - MAX_SIZE}"
            val itemToDelete = keyValueStorage[itemCountToDelete]

            itemToDelete?.let {
                keyValueStorage.unset(itemCountToDelete)
                keyValueStorage.unset("$itemToDelete-lastState")
            }
        }
    }
}
