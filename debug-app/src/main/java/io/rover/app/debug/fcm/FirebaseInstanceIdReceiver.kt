package io.rover.app.debug.fcm

import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.FirebaseInstanceIdService
import io.rover.core.Rover
import io.rover.notifications.PushReceiverInterface

class FirebaseInstanceIdReceiver : FirebaseInstanceIdService() {
    override fun onTokenRefresh() {
        Rover.shared?.resolve(PushReceiverInterface::class.java)?.onTokenRefresh(
            FirebaseInstanceId.getInstance().token
        )
    }
}
