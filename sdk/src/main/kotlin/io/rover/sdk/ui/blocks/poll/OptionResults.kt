package io.rover.sdk.ui.blocks.poll

import io.rover.sdk.data.graphql.putProp
import io.rover.sdk.data.graphql.toStringIntHash
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