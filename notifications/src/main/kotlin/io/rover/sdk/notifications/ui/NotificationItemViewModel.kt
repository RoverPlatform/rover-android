package io.rover.sdk.notifications.ui

import android.graphics.Bitmap
import io.rover.sdk.core.logging.log
import org.reactivestreams.Publisher
import io.rover.sdk.core.streams.flatMap
import io.rover.sdk.notifications.domain.Notification
import io.rover.sdk.notifications.domain.NotificationAttachment
import io.rover.sdk.core.assets.AssetService
import io.rover.sdk.core.streams.Publishers
import io.rover.sdk.notifications.ui.concerns.NotificationItemViewModelInterface

class NotificationItemViewModel(
        private val notificationItem: Notification,
        private val assetService: AssetService
) : NotificationItemViewModelInterface {
    override val showThumbnailArea: Boolean =
        notificationItem.attachment != null &&
            notificationItem.attachment is NotificationAttachment.Image

    override val notificationForDisplay: Notification
        get() = notificationItem

    override fun requestThumbnailImage(): Publisher<Bitmap> {
        return if (notificationItem.attachment == null || notificationItem.attachment !is NotificationAttachment.Image) {
            Publishers.empty()
        } else {
            assetService.getImageByUrl(notificationItem.attachment.url).flatMap { attachmentResult ->
                when (attachmentResult) {
                    is io.rover.sdk.core.data.NetworkResult.Error -> {
                        log.w("Unable to fetch notification item image: ${attachmentResult.throwable.message}")
                        Publishers.empty()
                    }
                    is io.rover.sdk.core.data.NetworkResult.Success -> {
                        Publishers.just(attachmentResult.response)
                    }
                }
            }
        }
    }
}
