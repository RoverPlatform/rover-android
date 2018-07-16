# Rover Android SDK

SDK 2.0 is under development, and is not yet available.  Please continue with
the [1.x series](https://github.com/RoverPlatform/rover-android/tree/master) for
now.

The in-development README for 2.x follows.

<hr />

## Plugins Overview

1. Data Plugin
2. Location Plugin
3. Push Plugin
4. User Experience Plugin
5. Events Plugin

## Requirements



## Setup and Usage

1. adding to build
2. intro Rover.initialize/assemblers

### Data Plugin

### User Experience Plugin

### Push Plugin

The Rover Push Plugin allows you to receive push notifications.  It has several
dependencies, namely the Google Firebase Cloud Messaging push notifications
platform and also a Notification-appropriate design asset from your app.

If you need to integrate with the legacy GCM offering from Google in lieu of
Firebase Cloud Messaging, please refer to
[README.legacy-gcm.md](README.legacy-gcm.md).

* discuss android notification icon design guidelines (multilayered drawable, etc.)
* discuss channels

Add the assembler for it to Rover.initialize().  You will need to specify your
small icon drawable resource id (which itself should be a LayeredDrawable as per
the Material Design guidelines).

```kotlin
PushPluginAssembler(
    applicationContext: this,
    smallIconResId: R.drawable.ic_icon,
    smallIconDrawableLevel: 1,
    defaultChannelId: 0
)
```

#### Add Firebase and Firebase Cloud Messaging to your App

Google uses their Firebase platform for their Android push offering, Firebase
Cloud Messaging.

Follow the directions at [Firebase -> Get Started ->
Android](https://firebase.google.com/docs/android/setup) to add the base
Firebase platform to your app and set up your account tokens.

#### Receive a Push Token from Firebase Cloud Messaging

You need to follow all of the standard setup for receiving FCM push messages in
your app.  You will need to follow the usual guidance from Google on integrating
Firebase into your Android client app at [Firebase -> Cloud Messaging -> Android
-> Set Up](https://firebase.google.com/docs/cloud-messaging/android/client).
Rover has purposefully does none of these steps for you; the goal is to allow
you to integrate push as you see fit.

Examples are provided below, in Kotlin (although may be trivially adapted to
Java).

Those instructions will direct you to create your own implementation of
`FirebaseInstanceIdService` receive the registered FCM push tokens.  You'll
indeed want to do so, and then pass the received push token along to Rover:

```kotlin
class MyAppFirebaseInstanceIdReceiver: FirebaseInstanceIdService() {
    override fun onTokenRefresh() {
        Rover.sharedInstance.pushReceiver.onTokenRefresh(
            FirebaseInstanceId.getInstance().token
        )
    }
}
```

One that is done, you will then need to implement a receiver for push messages
themselves.  Follow the guidance at [Firebase -> Cloud Messaging -> Android ->
Receive
Messages](https://firebase.google.com/docs/cloud-messaging/android/receive) to
create your implementation of `FirebaseMessagingService` and add the
`onMessageReceived` template callback method to it.

Once you have your empty `onMessageReceived` method ready to go, this is the
part where you delegate to the Rover SDK to create the notification in the
user's Android notification area by calling `onMessageReceivedData` on the Rover
push plugin:

```kotlin
class MyAppFirebaseMessageReceiver: FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Rover.sharedInstance.pushReceiver.onMessageReceivedData(
            remoteMessage.data
        )
    }
}
```

Note that the `Receive Messages` Firebase documentation section you are looking
at has a section that discusses notification appearance customization in the app
manifest (eg. setting the icon and color with `<meta-data>` tags).  However,
that only applies to so-called "Display Messages", where the notification is put
in the Android tray for you by Firebase Cloud Messaging.  Rover push
notifications never use this method, and instead the above `onMessageReceived()`
regime is responsible for always creating the notifications by node, and as such
the `<meta-data>` tags would not be used.  You will still need to pass your
small icon drawable to `PushPluginAssembler` at `Rover.initialize()` time as
discussed above.

4. wire up location and beacons
5. set up styles & brand colours
6. explore avenues for customization
7. 

### Events Plugin

## Reference Documentation

## Customization

### Require Login

### Add a Custom app view into an Experience flow

### Dynamically Modify Experiences

For example, if you put custom replacement directives or "variables" of your own
design into text in Experience blocks.

### Use a custom bundled font instead of Roboto

### Forgo the included Activity and Fragment, and use Rover embedded in your single-activity, fragmentless app.

### Other customisations

Consider reading through the below in order to glean the general pattern of
customisation.

Are you customising the Rover SDK in other ways than we've discussed here? We'd
love to hear about it!

### Custom Handling for Push Notifications

## Migrating from SDK 1.x

* changes to general design
  * fdafdsaf
* 

## Further Documentation
