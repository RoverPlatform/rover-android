package io.rover.notifications.graphql

import io.rover.notifications.domain.Notification
import io.rover.notifications.domain.NotificationAttachment
import io.rover.core.data.graphql.getDate
import io.rover.core.data.graphql.putProp
import io.rover.core.data.graphql.safeGetString
import io.rover.core.data.graphql.safeGetUri
import io.rover.core.data.graphql.safeOptDate
import io.rover.core.data.graphql.safeOptString
import io.rover.core.platform.DateFormattingInterface
import io.rover.core.platform.whenNotNull
import org.json.JSONException
import org.json.JSONObject
import java.net.URL

internal fun Notification.encodeJson(dateFormatting: DateFormattingInterface): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Notification::id, "id")
        putProp(this@encodeJson, Notification::title, "title")
        putProp(this@encodeJson, Notification::body, "body")
//        putProp(this@encodeJson, Notification::channelId, "channelId")
        putProp(this@encodeJson, Notification::isNotificationCenterEnabled, "isNotificationCenterEnabled")
        putProp(this@encodeJson, Notification::isRead, "isRead")
        putProp(this@encodeJson, Notification::isDeleted, "isDeleted")
        putProp(this@encodeJson, Notification::deliveredAt, "deliveredAt") { dateFormatting.dateAsIso8601(it) }
        putProp(this@encodeJson, Notification::expiresAt, "expiresAt") { it.whenNotNull { dateFormatting.dateAsIso8601(it) } }
        putProp(this@encodeJson, Notification::attachment, "attachment") { it.whenNotNull { it.encodeJson() } }
        putProp(this@encodeJson, Notification::tapBehavior, "tapBehavior") { it.encodeJson() }
        putProp(this@encodeJson, Notification::campaignId, "campaignID")
    }
}

internal fun NotificationAttachment.Companion.decodeJson(json: JSONObject): NotificationAttachment {
    // all three types have URLs.
    val type = json.safeGetString("type")
    val url = URL(json.safeGetString("url"))
    return when (type) {
        "AUDIO" -> NotificationAttachment.Audio(url)
        "IMAGE" -> NotificationAttachment.Image(url)
        "VIDEO" -> NotificationAttachment.Video(url)
        else -> throw JSONException("Unsupported Rover attachment type: $type")
    }
}

internal fun NotificationAttachment.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, NotificationAttachment::url, "url")
        put(
            "type",
            when (this@encodeJson) {
                is NotificationAttachment.Video -> "VIDEO"
                is NotificationAttachment.Audio -> "AUDIO"
                is NotificationAttachment.Image -> "IMAGE"
            }
        )
    }
}

internal fun Notification.Companion.decodeJson(json: JSONObject, dateFormatting: DateFormattingInterface): Notification {
    return Notification(
        id = json.safeGetString("id"),
        title = json.safeOptString("title"),
        body = json.safeGetString("body"),
//        channelId = json.safeOptString("channelId"), TODO put this back
        channelId = null,
        isRead = json.getBoolean("isRead"),
        isDeleted = json.getBoolean("isDeleted"),
        expiresAt = json.safeOptDate("expiresAt", dateFormatting),
        deliveredAt = json.getDate("deliveredAt", dateFormatting),
        isNotificationCenterEnabled = json.getBoolean("isNotificationCenterEnabled"),
        tapBehavior = Notification.TapBehavior.decodeJson(json.getJSONObject("tapBehavior")),
        attachment = if (json.has("attachment") && !json.isNull("attachment")) NotificationAttachment.decodeJson(json.getJSONObject("attachment")) else null,
        campaignId = json.safeGetString("campaignID")
    )
}

internal fun Notification.TapBehavior.Companion.decodeJson(json: JSONObject): Notification.TapBehavior {
    val typeName = json.safeGetString("__typename")

    return when (typeName) {
        "OpenURLNotificationTapBehavior" -> Notification.TapBehavior.OpenUri(
            uri = json.safeGetUri("url")
        )
        "PresentWebsiteNotificationTapBehavior" -> Notification.TapBehavior.PresentWebsite(
            url = json.safeGetUri("url")
        )
        "OpenAppNotificationTapBehavior" -> Notification.TapBehavior.OpenApp()
        else -> throw JSONException("Unsupported Block TapBehavior type `$typeName`.")
    }
}

internal fun Notification.TapBehavior.encodeJson(): JSONObject {
    return JSONObject().apply {
        put("__typename", when (this@encodeJson) {
            is Notification.TapBehavior.OpenApp -> {
                "OpenAppNotificationTapBehavior"
            }
            is Notification.TapBehavior.OpenUri -> {
                putProp(this@encodeJson, Notification.TapBehavior.OpenUri::uri, "url") { it.toString() }
                "OpenURLNotificationTapBehavior"
            }
            is Notification.TapBehavior.PresentWebsite -> {
                putProp(this@encodeJson, Notification.TapBehavior.PresentWebsite::url) { it.toString() }
                "PresentWebsiteNotificationTapBehavior"
            }
        })
    }
}
