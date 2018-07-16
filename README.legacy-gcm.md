## Auxilliary instructions: Rover set up with Legacy GCM

Please refer to the main README for the bulk of the setup instructions.

To use the legacy Google Cloud Messaging service in lieu of the now-recommended
Firebase Cloud Messaging service, complete the procedure below.

You should only integrate Rover with GCM instead of FCM in the event where other
legacy code in your app is currently holding you back from migrating.

### Ensure GCM is Installed

You will need to ensure that you have followed all of the standard setup for
receiving GCM push messages in your app. You will need to follow the usual
guidance from Google on integrating Firebase into your Android client app at
[Google Cloud Messaging -> GCM Clients -> Set Up a Client App on
Android](https://developers.google.com/cloud-messaging/android/client). Rover
has purposefully does none of these steps for you; the goal is to allow you to
integrate push as you see fit.

In particular, as per that documentation, you will need to supply your own
a GcmListenerService to receive your push token from GCM.  They also suggest using an Intent Service to do the work on a background thread, 
It will look something like the following:

```kotlin
class  AppGcmInstanceIDListenerService: InstanceIDListenerService () {
    override fun onTokenRefresh() {
        // unlike in the Firebase version, this cannot be called on the main thread.
        Executors.newSingleThreadExecutor().execute {
            val instanceID = InstanceID.getInstance(this)
            val token = instanceID.getToken(
                getString(R.string.gcm_defaultSenderId),
                GoogleCloudMessaging.INSTANCE_ID_SCOPE, null
            )
            Rover.sharedInstance.pushPlugin.onTokenRefresh(
                token
            )
        }
    }
}
```

Remember to add it to your Manifest as per the Google documentation:

```xml
<service
    android:name="AppGcmInstanceIDListenerService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.android.gms.iid.InstanceID" />
    </intent-filter>
</service>
```

### Handle incoming GCM messages

Then you'll need a `GcmListenerService` to actually receive the data push
notifications:

```kotlin
class AppGcmListenerService: GcmListenerService() {
    override fun onMessageReceived(from: String?, data: Bundle) {
        val pushPlugin = Rover.sharedInstance.pushPlugin
        pushPlugin.onMessageReceivedDataAsBundle(data)
    }
}
```

Naturally, it too must be properly registered in your Manifest as per the Google
documentation.

```xml
<service
    android:name="AppGcmListenerService"
    android:exported="false" >
    <intent-filter>
        <action android:name="com.google.android.c2dm.intent.RECEIVE" />
    </intent-filter>
</service>
```

Then to bring it together you will then need to declare one of Google GCM's own
services in your Manifest XML, as well:

```xml
<receiver
    android:name="com.google.android.gms.gcm.GcmReceiver"
    android:exported="true"
    android:permission="com.google.android.c2dm.permission.SEND" >
    <intent-filter>
        <action android:name="com.google.android.c2dm.intent.RECEIVE" />
        <category android:name="com.example.gcm" />
    </intent-filter>
</receiver>
```

