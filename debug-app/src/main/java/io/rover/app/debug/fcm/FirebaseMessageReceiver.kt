package io.rover.app.debug.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.rover.core.Rover
import io.rover.notifications.PushReceiverInterface

class FirebaseMessageReceiver : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Rover.shared?.resolve(PushReceiverInterface::class.java)?.onMessageReceivedData(
            remoteMessage.data
        )
    }
}
