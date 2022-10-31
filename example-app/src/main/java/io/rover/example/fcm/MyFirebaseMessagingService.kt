package io.rover.Example.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.rover.core.Rover
import io.rover.notifications.PushReceiverInterface

class MyFirebaseMessagingService: FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Rover.shared?.resolve(PushReceiverInterface::class.java)?.onTokenRefresh(
            token
        )
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Rover.shared?.resolve(PushReceiverInterface::class.java)?.onMessageReceivedData(
            remoteMessage.data
        )
    }
}