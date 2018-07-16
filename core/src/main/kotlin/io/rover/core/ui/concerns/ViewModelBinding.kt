package io.rover.core.ui.concerns

import org.reactivestreams.Subscription
import kotlin.reflect.KProperty

/**
 * Use this Kotlin delegated property to manage binding of a view model to a View.
 *
 * When rebinding to a new view model, it will unsubscribe/cancel any existing subscriptions
 * to asynchronous events.
 *
 * @param binding Pass in a closure that will set up your view as per the view model's direction. It
 * will provide you with a callback you can call for whenever a subscription for a subscriber you
 * created becomes ready.
 */
class ViewModelBinding<VM: Any>(
    private val rebindingAllowed: Boolean = true,
    private val binding: (viewModel: VM?, subscriptionCallback: (Subscription) -> Unit) -> Unit
) {
    private var activeViewModel: VM? = null
    private var outstandingSubscriptions : List<Subscription> = emptyList()

    operator fun getValue(thisRef: Any, property: KProperty<*>): VM? {
        return activeViewModel
    }

    operator fun setValue(thisRef: Any, property: KProperty<*>, value: VM?) {
        // cancel any existing async subscriptions.
        outstandingSubscriptions.forEach { subscription -> subscription.cancel() }
        outstandingSubscriptions = emptyList()

        if(activeViewModel != null && !rebindingAllowed) {
            throw RuntimeException("This view does not support being re-bound to a new view model.")
        }

        activeViewModel = value

        binding(value) { subscription ->
            if(activeViewModel == value) {
                // a subscription has come alive for currently active view model.
                outstandingSubscriptions += listOf(subscription)
            } else {
                // subscription for a stale view model has come up.  cancel it immediately.
                subscription.cancel()
            }
        }
    }
}

// How we are going to implement unsubscribe.

// facts:

// -> multiple view models can be assigned to a single view in succession.
// -> it is important to unsubscribe any Subscriptions created as part of binding the view model
// -> subscriptions are delivered asynchronously in Reactive Streams, argh.
// -> view model can be unbound by setting null
// -> there can be more than one subscription involved
// -> stock android sets view models by having the View itself reach out to a "provider" object to get its viewmodel.

// plan:

// create a delegated property type, with a generic type parameter of the view model and a block
// of how to do the binding. that block will return a *list* of subscriptions. on next rebind,
// the delegated property will cancel all of those subscriptions.

// hm. maybe a separate block "unsettling" which would do all the default clearing of the fields?
// or just stick with the null-if statement and conditional execution patterns we have now,
// which