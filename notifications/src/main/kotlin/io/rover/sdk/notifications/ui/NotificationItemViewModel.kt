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

package io.rover.sdk.notifications.ui

import android.graphics.Bitmap
import io.rover.sdk.core.assets.AssetService
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.streams.Publishers
import io.rover.sdk.core.streams.flatMap
import io.rover.sdk.notifications.domain.Notification
import io.rover.sdk.notifications.domain.NotificationAttachment
import io.rover.sdk.notifications.ui.concerns.NotificationItemViewModelInterface
import org.reactivestreams.Publisher

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
