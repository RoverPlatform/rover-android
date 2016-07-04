# Android SDK Integration

## Requirements
  - Android Studio 2.1.2 or higher
  - Min Android SDK 18
  - GooglePlayServices 9.0.2 on device
  - Firebase account
  
## Installing the Library

Before continueing with the installation of the Rover SDK, please make sure you have setup you project and Android app in the [Firebase Console](https://console.firebase.google.com/).

### JCenter

The easiest way to get Rover into your Android project is to use the [JCenter](https://bintray.com/bintray/jcenter) Maven repository. Just add the following line to the `dependencies` section of your module's `build.gradle` file:

```
compile 'io.rover.library:rover:0.2.1'
```

### Manual Installation

// COMING SOON

## Intializing the SDK

The Rover SDK **MUST** be initialized inside the Application base class `onCreate` method. If your Android application doesn't already have an Application base class, follow [these](https://developer.android.com/reference/android/app/Application.html) instructions to create one.

Add the following snippet to your Application's `onCreate` method:

```java
  RoverConfig config = new RoverConfig.Builder()
          .setApplicationToken("YOUR APPLICATION TOKEN HERE")
          .build();

  Rover.setup(this, config);
```

## Monitoring for Beacons and Geofences

Call the `startMonitoring()` method to begin monitoring for beacons and geofences. You may choose to do this step in any Activity of your choice. 

__IMPORTANT__ 
As of the Marshmallow release, the Android OS requries this to be wrapped in a permission check block. An example of a backwards compatible way to do this is shown below:

```java
public class MainActivity extends AppCompatActivity {
  public void startRoverMonitoring() {
        if (Build.VERSION.SDK_INT >= 23) {
            // Marshmallow+
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
            } else {
                Rover.startMonitoring();
            }
        } else {
            // Pre-Marshmallow
            Rover.startMonitoring();
        }
  }
  
  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
      if (requestCode == 0) {
          if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
              Rover.startMonitoring();
          }
      } else {
          super.onRequestPermissionsResult(requestCode, permissions, grantResults);
      }
  }
}
```

### Proximity Events

Rover uses the observer pattern to notify the developer of proximity events. The `Rover.addObserver();` method accepts any object implementing one or more of the [RoverObservers](https://github.com/RoverPlatform/rover-android/blob/master/rover/src/main/java/io/rover/RoverObserver.java).

Here's an example of an `Activity` that adds itself as an observer and implements the `MessageDeliveryObserver` interface.

```java
public class MyActivity extends AppCompatActivity implements RoverObserver.MessageDeliveryObserver {
    @Override
    protected void onCreate(bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Rover.addObserver(this);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        Rover.removeObserver(this);
    }
    
    @Override
    public void onMessageReceived(Message message) {
        Log.i("RoverMessage", "Received message: " + message.getText());
    }
}
```

__IMPORTANT__ Notice that the example removes itself as an observer in the `onDestory` method. This is required in order for the class to properly deallocate itself. Any call to `Rover.addObserver();` _must_ be balanced with a corresponding call to `Rover.removeObserver();`.

### Beacons and Places

// Coming soon 

## Messages

// Coming soon

### Notifications

In order to have notification and messages working, Rover needs your FCM server key. Use [this guide](https://github.com/RoverPlatform/rover-android/wiki/FCM-Setup) to upload your configure Rover with your FCM setup.

If you like fine-grained control over notifications, you must register a [NotificationProvider](https://github.com/RoverPlatform/rover-android/blob/master/rover/src/main/java/io/rover/NotificationProvider.java) during initialization.

```java
  RoverConfig config = new RoverConfig.Builder()
          .setApplicationToken("YOUR APPLICATION TOKEN HERE")
          .setNotificationProvider(new NotificationProvider() {
            ...
          })
          .build();

  Rover.setup(this, config);
```

Check the [Notification Provider](https://github.com/RoverPlatform/rover-android/blob/master/rover/src/main/java/io/rover/NotificationProvider.java) file for more documentation on methods to customize behavior.
