package io.rover;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.net.Uri;


/**
 * Created by Rover Labs Inc on 2016-07-04.
 */
public interface NotificationProvider {
    /**
     * Override this method if you would like to provide a custom pending intent for the
     * notification associated with the given Message.
     *
     * @param message The message for which a notification will be posted.
     * @return A pending intent you would like to execute when user taps on the notification.
     * Returning null will fallback to default Rover behaviour.
     */
    PendingIntent getNotificationPendingIntent(io.rover.model.Message message);

    /**
     * Override this method if you would like to provide a tray icon for the notification
     * associate with the given Message.
     *
     * @param message The message for which a notification will be posted.
     * @return An integer representing an asset to display in the tray menu. Normally something
     * like `R.my_icon`.
     * Returning null will use default Rover tray icon.
     */
    int getSmallIconForNotification(io.rover.model.Message message);

    /**
     * Override this method if you would like to provide a large icon for the notification
     * associated with the given Message.
     *
     * @param message The message for which a notification will be posted.
     * @return A Bitmap to display in the notification content as an icon.
     */
    Bitmap getLargeIconForNotification(io.rover.model.Message message);

    /**
     * Override this method if you would like to provide a custom sound for the notification
     * associated with the given Message.
     *
     * @param message The message for which a notification will be posted.
     * @return A Uri to the sound file you would like to play when the notification is posted.
     */
    Uri getSoundForNotification(io.rover.model.Message message);
}