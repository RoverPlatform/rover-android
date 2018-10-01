package io.rover.core.data.sync

import io.rover.core.data.graphql.safeOptString
import org.json.JSONObject

fun PageInfo.Companion.decodeJson(json: JSONObject): PageInfo {
    return PageInfo(
        json.safeOptString("endCursor"),
        json.getBoolean("hasNextPage")
    )
}