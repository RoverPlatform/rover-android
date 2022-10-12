# Rover Android SDK

## Rover Android SDK 3.0

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
    implementation "io.rover:sdk:3.8.2"
}
```

Please continue onwards from https://developer.rover.io/android/.
