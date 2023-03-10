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

import io.rover.sdk.core.data.graphql.operations.data.toStringIntHash
import io.rover.sdk.core.data.graphql.putProp
import org.json.JSONObject

internal data class OptionResults(val results: Map<String, Int>) {
    fun encodeJson(): JSONObject {
        return JSONObject().apply {
            putProp(this@OptionResults, OptionResults::results) { JSONObject(it) }
        }
    }

    companion object {
        fun decodeJson(jsonObject: JSONObject): OptionResults {
            return OptionResults(
                results = jsonObject.getJSONObject("results").toStringIntHash()
            )
        }
    }
}

private data class VotesWithFractional(val key: String, var votePercentage: Int, val fractional: Float)

internal fun changeVotesToPercentages(results: OptionResults): OptionResults {
    // https://en.wikipedia.org/wiki/Largest_remainder_method
    val total = results.results.values.sum().toFloat()
    val votes = results.results.mapValues { (it.value.toFloat() / total * 100) }

    val votesWithFractional = votes.toList().sortedBy { it.first }.map {
        VotesWithFractional(
            it.first,
            it.second.toInt(),
            it.second - it.second.toInt()
        )
    }

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
