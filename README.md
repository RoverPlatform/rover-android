# Rover Campaigns Android SDK

## Rover Campaigns Android SDK 3.0

The first step is to add the library dependencies. We’ll start with a default
installation, featuring all of the Rover libraries.

Ensure that you have Rover's maven repository added to the `dependencies` →
`repositories` block of your app-level `build.gradle`:

```groovy
dependencies {
    // ...
    repositories {
        // ...
        maven {
            url "https://judoapp.github.io/judo-maven/maven"
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
    implementation "io.rover.campaigns:core:3.10.0"
    implementation "io.rover.campaigns:notifications:3.10.0"
    implementation "io.rover.campaigns:location:3.10.0"
    implementation "io.rover.campaigns:debug:3.10.0"
    implementation "io.rover.campaigns:experiences:3.10.0"
}
```

Please continue onwards from https://github.com/RoverPlatform/rover-campaigns-android/wiki.
