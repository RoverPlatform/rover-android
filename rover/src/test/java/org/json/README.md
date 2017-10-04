# An inline copy of Android's version of `org.json` for testing

This is a copy of the `org.json` reimplementation done by the Android
team.  This was done to enable JSON serialization testing to work.

During normal operation the Rover SDK uses the copy included in the
Android standard library.  However, at test runtime, the test code
will find itself running on the Oracle VM on the developer's workstation
with a stubbed version of `android.jar`, including the org.json library.

This means all attempts to do any sort of integration testing of
the Rover SDK code responsible for serializing JSON will fail with stub
exceptions.

The workaround here is to include the real Android org.json code here
for the test builds of the Rover SDK library, where it appears to
suitably shadow the stubbed version from `android.jar`.

From the `json` directory of the `nougat-mr2.3-release` branch of the
https://android.googlesource.com/platform/libcore git repository.

Licensed by The Android Open Source Project under Apache v2.
