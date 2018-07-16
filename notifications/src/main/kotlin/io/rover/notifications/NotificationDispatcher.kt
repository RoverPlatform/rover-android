package io.rover.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.support.annotation.DrawableRes
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import io.rover.notifications.domain.NotificationAttachment
import io.rover.notifications.ui.concerns.NotificationsRepositoryInterface
import io.rover.core.R
import io.rover.core.assets.AssetService
import io.rover.core.data.NetworkResult
import io.rover.core.logging.log
import io.rover.core.streams.Publishers
import io.rover.core.streams.Scheduler
import io.rover.core.streams.doOnNext
import io.rover.core.streams.map
import io.rover.core.streams.onErrorReturn
import io.rover.core.streams.subscribe
import io.rover.core.streams.subscribeOn
import io.rover.core.streams.timeout
import org.reactivestreams.Publisher
import java.util.concurrent.TimeUnit

/**
 * Responsible for adding a [Notification] to the Android notification area as well as the Rover
 * Notification Center.
 */
class NotificationDispatcher(
    private val applicationContext: Context,

    private val mainThreadScheduler: Scheduler,

    // add this back after solving injection issues.
    private val notificationsRepository: NotificationsRepositoryInterface,

    private val notificationOpen: NotificationOpenInterface,

    private val assetService: AssetService,

    private val influenceTrackerService: InfluenceTrackerServiceInterface,

    /**
     * A small icon is necessary for Android push notifications.  Pass a resid.
     *
     * Android design guidelines suggest that you use a multi-level drawable for your application
     * icon, such that you can specify one of its levels that is most appropriate as a single-colour
     * silhouette that can be used in the Android notification drawer.
     */
    @param:DrawableRes
    private val smallIconResId: Int,

    /**
     * The drawable level of [smallIconResId] that should be used for the icon silhouette used in
     * the notification drawer.
     */
    private val smallIconDrawableLevel: Int = 0,

    /**
     * This Channel Id will be used for any push notifications arrive without an included Channel
     * Id.
     */
    private val defaultChannelId: String? = null
) {
    fun ingest(notificationFromAction: io.rover.notifications.domain.Notification) {
        Publishers.defer {
            processNotification(notificationFromAction)
        }.subscribeOn(mainThreadScheduler).subscribe {} // TODO: do not use subscriber like this, it will leak
    }

    /**
     * By default, if running on Oreo and later, and the [PushReceiver.defaultChannelId] you
     * gave does not exist, then we will lazily create it at notification reception time to
     * avoid the
     *
     * We include a default implementation here, however, you should consider registering your own
     * channel ID in your application initialization and passing it to the NotificationAssembler()
     * constructor.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun registerDefaultChannelId() {
        log.w("Rover is registering a default channel ID for you.  This isn't optimal; if you are targeting Android SDK >= 26 then you should create your Notification Channels.\n" +
            "See https://developer.android.com/training/notify-user/channels.html")
        // Create the NotificationChannel
        val name = applicationContext.getString(R.string.default_notification_channel_name)
        val description = applicationContext.getString(R.string.default_notification_description)

        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel(defaultChannelId, name, importance)
        mChannel.description = description
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        val notificationManager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
    }


    private fun processNotification(notification: io.rover.notifications.domain.Notification): Publisher<Unit> {
        // notify the influenced opens tracker that a notification is being executed.
        influenceTrackerService.notifyNotificationReceived(notification)

        verifyChannelSetUp()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(applicationContext, notification.channelId ?: defaultChannelId ?: NotificationChannel.DEFAULT_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") // Only in use to support legacy Android API.
            NotificationCompat.Builder(applicationContext)
        }

        builder.setContentTitle(notification.title)
        builder.setContentText(notification.body)
        builder.setSmallIcon(smallIconResId, smallIconDrawableLevel)

        notificationsRepository.notificationArrivedByPush(notification)

        builder.setContentIntent(
            notificationOpen.pendingIntentForAndroidNotification(
                notification
            )
        )

        // Set large icon and Big Picture as needed by Rich Media values.  Enforce a timeout
        // so we don't fail to create the notification in the allotted 10s if network doesn't
        // cooperate.
        val attachmentBitmapPublisher = when(notification.attachment) {
            is NotificationAttachment.Image -> {
                assetService.getImageByUrl(notification.attachment.url)
                    .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .onErrorReturn { error ->
                        // log.w("Timed out fetching notification image.  Will create image without the rich media.")
                        NetworkResult.Error(error, false)
                    }
            }
            null -> Publishers.just(null)
            else -> {
                log.w("Notification attachments of type ${notification.attachment.typeName} not supported on Android.")
                Publishers.just(null)
            }
        }

        return attachmentBitmapPublisher
            .doOnNext { attachmentBitmapResult ->
                when(attachmentBitmapResult) {
                    is NetworkResult.Success -> {
                        builder.setLargeIcon(attachmentBitmapResult.response)
                        builder.setStyle(
                            NotificationCompat.BigPictureStyle()
                                .bigPicture(attachmentBitmapResult.response)
                        )
                    }
                    is NetworkResult.Error -> {
                        log.w("Unable to retrieve notification image: ${notification.attachment?.url}, because: ${attachmentBitmapResult.throwable.message}")
                        log.w("Will create image without the rich media.")
                    }
                }
                // TODO: don't use '123'.
                notificationManager.notify(notification.id, 123, builder.build().apply { this.flags = this.flags or Notification.FLAG_AUTO_CANCEL })
            }.map { Unit }
    }

    private val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(applicationContext)

    @SuppressLint("NewApi")
    private fun verifyChannelSetUp() {
        val notificationManager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        val existingChannel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.getNotificationChannel(defaultChannelId)
        } else {
            return
        }

        if(existingChannel == null) registerDefaultChannelId()
    }

    companion object {
        /**
         * Android gives push handlers 10 seconds to complete.
         *
         * If we can't get our image downloaded in the 10 seconds, instead of failing we want to
         * timeout gracefully.
         */
        private const val TIMEOUT_SECONDS = 8L
    }
}