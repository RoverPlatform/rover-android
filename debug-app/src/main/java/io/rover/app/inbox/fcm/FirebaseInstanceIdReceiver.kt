package io.rover.app.inbox.fcm

import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.FirebaseInstanceIdService
import io.rover.core.Rover
import io.rover.notifications.pushReceiver

class FirebaseInstanceIdReceiver : FirebaseInstanceIdService() {
    override fun onTokenRefresh() {
        Rover.sharedInstance.pushReceiver.onTokenRefresh(
            FirebaseInstanceId.getInstance().token
        )
    }
}
