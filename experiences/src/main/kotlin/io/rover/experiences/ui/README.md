# Rover SDK Experiences UI Layer Internals

Note that developers seeking to integrate the Rover SDK should not
typically need to delve here, but those wanting to extend the
Experiences system in deeper ways, Rover's own developers, and the
simply curious will likely find this valuable.

## Layout System

-> we more or less replace the standard Android layout, and the
BlockAndRowLayoutManager places things in the pixel-exact positions
described by our own layout.

## View Models

-> must be a pure DAG

... question, is it OK to use a Subject to invert responsibility to keep
that DAG? It means we'd use calls into a view model instead of having
that view model subscribe to something in some cases.  Which is kind of
a weird switch of pattern.

## View Model Mixins

-> interface delegation approach

-> all ultimately must be merged into a single interface in a top-level
View Model that is meant to be bound directly to a view (and own that
view).  Class delegation is a good method of achieving this.

## Views

## View Mixins

## Observable Chains

We wanted to avoid introducing a dependency on the Android Architecture
libraries (specifically, the LiveData components), so instead we
implemented designed our own lightweight reactive MVVM.  The principles,
however, remain generally the same.

-> UI state is effectively event-sourced.

-> events for both updating the UI and informing client view models of
actions.

-> we've implemented a small, minimalist, built-in version of
Rx/Reactive Streams to avoid any external dependencies.

-> any side-effects must occur before a share() or shareAndReplay()
operator (updating state, calling a method on another view model, etc.).

-> new subscribers (particularly views) should immediately receive
enough events to fully populate them (this is similar behaviour to
LiveData).  This adds some complexity, however, because not all of the
events are appropriate to re-emit (some are idempotent, like updating a
list, and others are not, like displaying a snackbar).   An important
constraint is that this re-emission behaviour must not conflict with
avoiding duplicating side-effect operations.  Our solution to this
problem is adding a stage to the end of the pipeline that will re-emit
any previously observed items that match a list of given types.

Perhaps one solution to this problem is to implement the re-emitter as
the last stage of an observable chain (merge with a flatMap on the
shared epic that eats all the original events but re-emits on
subscribe), that has a whitelist of events to buffer and re-emit on
subscribe.  The prior steps (along with any side-effects) can then
shared.

Note that there is also the possibility of a race condition if you
update state conditionally on the current state in response to a
subscription, particularly if you perform a non-idempotent side-effect.
If there is any asynchronous behaviour in the chain (even if you use
share()), then any other subscriptions that come in before the first
completes and performs its side-effect, then that behaviour will occur
that many times. This will not happen with any of the share*()
operators, however, so be mindful when storing local state outside of
shareAndReplay().

-> (should discuss the "refresh on subscribe" pattern, too.   So, we are
often doing a cached result)

-> action dispatch.  views with user-actionable behaviours call methods
on view model, those methods are "action creators" that create an Action
object and emit it through a PublishSubject.  Use an enum or Kotlin
sealed class type private to the view model implementation to model
these.

-> try to have all the behaviour in a single observable chain, the
'epic', which is rooted on an Observable (say from a network service
injected into the view model), and then merges or flatMaps in the
actions subject discussed above.  That is, the role of the Epic is to
transform external input (remote service data, user input) into messages
that describe UI state. However, make sure to break apart the epic into
methods to avoid having a single massive expression.

-> constrast with LiveData:
  -> single Observable exit point rather than multiple
  -> greater flexibility
  -> can use functional-reactive style


## State Persistence

-> responsibility for this lies in the view models

-> Only persist state in the Android state bundle when there's draft
data that belongs to the user (even if it's just a scroll position),
that does not have its source of truth anywhere else.  This is fine from
a performance perspective: the Services that the view interacts with are
responsible for maintaining fast multi-layered (including in-memory)
caches.

-> (discuss the view models' parcelized icicles and injection thereof)

-> our icicles are Parcelables so that in the event of non-process death
then the serialization roundtrip is avoided.