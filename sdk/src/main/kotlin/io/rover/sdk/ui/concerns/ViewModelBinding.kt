package io.rover.sdk.ui.concerns

import android.view.View
import io.rover.sdk.logging.log
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
internal class ViewModelBinding<VM : Any>(
    view: View? = null,
    private val rebindingAllowed: Boolean = true,
    private val cancellationBlock:(() -> Unit)? = null,
    private val binding: (viewModel: VM?, subscriptionCallback: (Subscription) -> Unit) -> Unit
) {
    private var activeViewModel: VM? = null
    private var outstandingSubscriptions: List<Subscription>? = null
    init {
        val onStateChangedListener = object : View.OnAttachStateChangeListener {
            override fun onViewDetachedFromWindow(v: View?) {
                cancelSubscriptions()
            }

            override fun onViewAttachedToWindow(v: View?) {
                // If we are being re-attached without having been rebound, then we want to re-bind to the existing ViewModel (particularly to re-establish any subscriptions that were cancelled on detach)
                if (outstandingSubscriptions == null && rebindingAllowed) {
                    activeViewModel?.let { invokeBinding(it) }
                }
            }
        }

        view?.addOnAttachStateChangeListener(onStateChangedListener)
    }

    operator fun getValue(thisRef: Any, property: KProperty<*>): VM? {
        return activeViewModel
    }

    private fun cancelSubscriptions() {
        // cancel any existing async subscriptions.
        outstandingSubscriptions?.forEach { subscription -> subscription.cancel() }
        outstandingSubscriptions = null
        cancellationBlock?.invoke()
    }

    private fun invokeBinding(value: VM?) {
        binding(value) { subscription: Subscription ->
            if (activeViewModel == value) {
                // a subscription has come alive for currently active view model!
                outstandingSubscriptions = listOf(subscription) + (outstandingSubscriptions ?: listOf())
            } else {
                // subscription for a stale view model has come up.  cancel it immediately.
                subscription.cancel()
            }
        }
    }

    operator fun setValue(thisRef: Any, property: KProperty<*>, value: VM?) {
        cancelSubscriptions()

        if (activeViewModel != null && !rebindingAllowed) {
            throw RuntimeException("This view does not support being re-bound to a new view model.")
        }

        activeViewModel = value
        invokeBinding(value)
    }
}
