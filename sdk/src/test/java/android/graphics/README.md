## Inlined copy of android.graphics.Rect and RectF for tests

`android.graphics.Rect` and `RectF` ship with the stdlib/Android SDK,
and while it's a pretty abstract thing that has no Android runtime
dependencies, it is regardless stubbed out when running tests.

Our code under test makes liberal use of these while otherwise avoiding
Android dependencies.  Thus, we elected to shadow the SDK's version of
Rect with a copied version at test time.

This version is copied from Android AOSP 25.
