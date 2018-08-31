package io.rover.notifications.ui

import android.graphics.Bitmap
import io.rover.core.logging.log
import org.reactivestreams.Publisher
import io.rover.core.streams.flatMap
import io.rover.core.data.NetworkResult
import io.rover.notifications.domain.Notification
import io.rover.notifications.domain.NotificationAttachment
import io.rover.core.assets.AssetService
import io.rover.core.streams.Publishers
import io.rover.notifications.ui.concerns.NotificationItemViewModelInterface

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
                    is NetworkResult.Error -> {
                        log.w("Unable to fetch notification item image: ${attachmentResult.throwable.message}")
                        Publishers.empty()
                    }
                    is NetworkResult.Success -> {
                        Publishers.just(attachmentResult.response)
                    }
                }
            }
        }
    }
}
