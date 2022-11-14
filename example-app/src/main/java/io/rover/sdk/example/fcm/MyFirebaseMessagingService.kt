package io.rover.sdk.example.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.rover.sdk.core.Rover
import io.rover.sdk.notifications.PushReceiverInterface

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