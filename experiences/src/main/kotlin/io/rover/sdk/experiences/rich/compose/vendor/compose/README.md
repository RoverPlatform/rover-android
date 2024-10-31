## Vendored Jetpack Compose UI Components

A couple of Jetpack Compose components (namely Opacity and Mask) do not
tolerate Experiences' Packed Intrinsics values being passed through their intrinsics to
children, even though conceptually they do not change the size of the child.

This is usually because for any LayoutModifier, the default behaviour is not to
just to delegate the intrinsics methods to the child, but rather, to have a
default behaviour that puts Measurable facades on any IntrinsicMeasurable
children, and use the measure() method to approximate.

This is not suitable for Rover Experiences, and so that default behaviour will unfortunately
crash if exposed to a Packed Intrinsics value.

This means we have to vendor several Compose UI components just so we can patch
their measurement policies to do intrinsics pass-through.

### Changes

The methodology here was to copy the needed components over along with any of
their needed dependencies, and then making the below changes:

- Grab GraphicsLayerModifier and its needed dependencies (namely clip and
  graphicsLayer). Replace any `.layout()` modifiers with our
  `layoutWithIntrinsicsPassthrough()`.
- Graph Modifier.opacity(). Should now use graphicsLayer modifier brought over above.
- Modify BlockGraphicsLayerModifier (needed by clip and mask) to pass through intrinsics. Also add the same
  compensation to cancel centering done by Compose itself for larger children (see
  SimpleMeasurePolicy for details.)
- SimpleGraphicsLayerModifier needs the same treatment.
- move them all into the `io.rover.sdk.experiences.rich.compose.ui.vendor.compose.ui` package.
- mark them anything previously `public` as `internal` to avoid leaking them to clients.
- various changes to use vendored copy of any dependencies.

Any places where those modifications have been made has been marked with a
`ROVER:` comment.

If these need to be updated for a new major version of Compose, applying updates to these in
future will likely involve just running through that same procedure again.
