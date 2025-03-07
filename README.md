# Rover Android SDK

## Rover Android SDK 4.7

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
            url "https://roverplatform.github.io/rover-maven/maven"
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
    implementation "io.rover.sdk:core:4.7.0"
    implementation "io.rover.sdk:notifications:4.7.0"
    implementation "io.rover.sdk:location:4.7.0"
    implementation "io.rover.sdk:debug:4.7.0"
    implementation "io.rover.sdk:experiences:4.7.0"
}
```

Please continue onwards from https://developer.rover.io.
