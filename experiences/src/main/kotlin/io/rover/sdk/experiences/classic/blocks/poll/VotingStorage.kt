/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.experiences.classic.blocks.poll

import io.rover.sdk.core.logging.log
import io.rover.sdk.experiences.platform.KeyValueStorage
import org.json.JSONException
import org.json.JSONObject

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
