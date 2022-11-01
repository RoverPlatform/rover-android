package io.rover.experiences.ui.blocks.poll


import io.rover.core.data.graphql.operations.data.toStringIntHash
import io.rover.core.data.graphql.putProp
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