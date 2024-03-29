## Vendored Jetpack Compose Foundation & UI Components

Several Jetpack Compose components (namely Scroll, Opacity, and Mask) do not
tolerate Experiences' Packed Intrinsics values being passed through their intrinsics to
children, even though conceptually they do not change the size of the child.

This is usually because for any LayoutModifier, the default behaviour is not to
just to delegate the intrinsics methods to the child, but rather, to have a
default behaviour that puts Measurable facades on any IntrinsicMeasurable
children, and use the measure() method to approximate.

This is not suitable for Rover Experiences, and so that default behaviour will unfortunately
crash if exposed to a Packed Intrinsics value.

This means we have to vendor several Compose components just so we can patch
their measurement policies to do intrinsics pass-through.

### Changes

The methodology here was to copy the needed components over along with any of
their needed dependencies, and then making the below changes:

- Grab GraphicsLayerModifier and Scroll needed dependencies (including clip, overscroll, and
  graphicsLayer). Replace any `.layout()` modifiers with our
  `layoutWithIntrinsicsPassthrough()`.
- Graph Modifier.opacity(). Should now use graphicsLayer modifier brought over above.
- Modify BlockGraphicsLayerModifier (needed by clip() used by vendored Scroll as
  well as needed by Experiences's MaskModifier) to pass through intrinsics. Also add the same
  compensation to cancel centering done by Compose itself for larger children (see
  SimpleMeasurePolicy for details.)
- SimpleGraphicsLayerModifier needs the same treatment.
- move them all into the `io.rover.sdk.experiences.rich.compose.ui.vendor.[ui/foundation]` package.
- mark them anything previously `public` as `internal` to avoid leaking them to clients.
- various changes to use vendored copy of any dependencies.

Any places where those modifications have been made has been marked with a
`ROVER:` comment.

Unfortunately, applying updates to these in future will likely involve just
running through that same procedure again.

## Risks

While we were lucky that, after duplicating things to a certain depth, we were
indeed able to vendor these components without running into intractable issues
with Private API, many of them do depend on ExperimentalFoundationApi optins.

Jetpack Compose has proved to have unstable public APIs in the areas that the vendored items call,
even across minor releases with theoretically stable APIs. This means that for all Jetpack
Compose releases this procedure basically needs to be redone.

## Ongoing Strategy

We've also determined that we should stay abreast of the latest Jetpack Compose BoM release,
in order to avoid having our Compose dep brought forward to an incompatible release within
a customer's app.