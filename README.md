# Rover Android SDK

SDK 2.0 is available only as a beta release, and is not yet generally available.
Please continue with the [1.x
series](https://github.com/RoverPlatform/rover-android/tree/master) for now
unless you have spoken with your CSM.

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
    implementation "io.rover:core:2.0.0-beta.3-SNAPSHOT"
    implementation "io.rover:notifications:2.0.0-beta.3-SNAPSHOT"
    implementation "io.rover:experiences:2.0.0-beta.3-SNAPSHOT"
    implementation "io.rover:location:2.0.0-beta.3-SNAPSHOT"
    implementation "io.rover:debug:2.0.0-beta.3-SNAPSHOT"
}
```

Please continue onwards from https://www.rover.io/docs/android/.