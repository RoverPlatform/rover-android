# Rover Android SDK

If you are currently using Rover SDK 1.x, please see the latest [1.x release
README](https://github.com/RoverPlatform/rover-android/tree/7c7649a1c69c64927db36d84d4b6d666341b1393).

<hr />

## Rover Android SDK 2.0

The first step is to add the library dependencies.  We’ll start with a default
installation, featuring all of the Rover libraries.

Ensure that you have Rover's maven repository added to the `dependencies` →
`repositories` block of your app-level `build.gradle`:

```groovy
dependencies {
    // ...
    repositories {
        // ...
        maven {
            url "http://roverplatform.github.io/rover-android/maven"
        }
    }
}
```

Then add the following to your application-level `build.gradle` file (not the
top level `build.gradle`, but rather your app-level one) in the `dependencies`
block.
 
```groovy
dependencies {
    // ...
    implementation "io.rover:core:2.2.9"
    implementation "io.rover:notifications:2.2.9"
    implementation "io.rover:experiences:2.2.9"
    implementation "io.rover:location:2.2.9"
    implementation "io.rover:debug:2.2.9"
}
```

Please continue onwards from https://developer.rover.io/android/.
