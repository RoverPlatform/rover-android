package io.rover.campaigns.core.data.sync

import io.rover.campaigns.core.data.graphql.safeOptString
import org.json.JSONObject

fun PageInfo.Companion.decodeJson(json: JSONObject): PageInfo {
    return PageInfo(
        json.safeOptString("endCursor"),
        json.getBoolean("hasNextPage")
    )
}