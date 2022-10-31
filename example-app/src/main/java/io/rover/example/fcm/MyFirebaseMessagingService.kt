package io.rover.Example.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.rover.campaigns.core.RoverCampaigns
import io.rover.campaigns.notifications.PushReceiverInterface

class MyFirebaseMessagingService: FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        RoverCampaigns.shared?.resolve(PushReceiverInterface::class.java)?.onTokenRefresh(
            token
        )
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        RoverCampaigns.shared?.resolve(PushReceiverInterface::class.java)?.onMessageReceivedData(
            remoteMessage.data
        )
    }
}