package io.rover.sdk.ui.concerns

import android.view.View
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.blocks.poll.VisibilityAwareView
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
    // private var activeViewModel: VM? = null
    private var outstandingSubscriptions: List<Subscription>? = null

    @Suppress("UNCHECKED_CAST")
    private var viewState: ViewState<VM> = ViewState.Inactive(true, null, true)
    set(value) {
        field = value
        when (value) {
            is ViewState.Active<*> -> {
                if (outstandingSubscriptions == null) invokeBinding(value.value as VM)
            }
            else -> cancelSubscriptions()
        }
    }

    init {
        setupViewAttachListener(view)
        setupVisibilityAwareViewObserver(view)
    }

    private fun setupVisibilityAwareViewObserver(view: View?) {
        (view as? VisibilityAwareView)?.let { visibilityAwareView ->
            visibilityAwareView.visibilitySubject.subscribe {  visibility ->
                viewState = if (visibility == View.VISIBLE) viewState.setForeground(true) else viewState.setForeground(false)
            }
        }
    }

    private fun setupViewAttachListener(view: View?) {
        val onStateChangedListener = object : View.OnAttachStateChangeListener {
            override fun onViewDetachedFromWindow(v: View?) { viewState = viewState.setAttached(false) }
            override fun onViewAttachedToWindow(v: View?) { viewState = viewState.setAttached(true) }
        }
        view?.addOnAttachStateChangeListener(onStateChangedListener)
    }

    operator fun setValue(thisRef: Any, property: KProperty<*>, value: VM?) {
        if (viewState.value != null && !rebindingAllowed) throw RuntimeException("This view does not support being re-bound to a new view model.")
        viewState = viewState.setVM(null)
        viewState = viewState.setVM(value)
    }

    operator fun getValue(thisRef: Any, property: KProperty<*>) = viewState.value

    private fun cancelSubscriptions() {
        // cancel any existing async subscriptions.
        outstandingSubscriptions?.forEach { subscription -> subscription.cancel() }
        outstandingSubscriptions = null
        cancellationBlock?.invoke()
    }

    private fun invokeBinding(value: VM?) {
        binding(value) { subscription: Subscription ->
            if (viewState.value == value) {
                // a subscription has come alive for currently active view model!
                outstandingSubscriptions = listOf(subscription) + (outstandingSubscriptions ?: listOf())
            } else {
                // subscription for a stale view model has come up.  cancel it immediately.
                subscription.cancel()
            }
        }
    }
}

sealed class ViewState<VM : Any> {
    abstract val value: VM?
    abstract fun setForeground(foregrounded: Boolean): ViewState<VM>
    abstract fun setAttached(attached: Boolean): ViewState<VM>
    abstract fun setVM(value: VM?): ViewState<VM>

    data class Active<VM : Any>(override val value: VM?) : ViewState<VM>() {
        override fun setForeground(foregrounded: Boolean): ViewState<VM> {
            return if (foregrounded) this else Inactive(false, value, true)
        }

        override fun setVM(value: VM?): ViewState<VM> {
            return if(value != null) Active(value) else Inactive(true, null, true)
        }

        override fun setAttached(attached: Boolean): ViewState<VM> {
            return if(attached) this else Inactive(true, value, false)
        }
    }
    data class Inactive<VM : Any>(val foregrounded: Boolean, override val value: VM?, val attached: Boolean) : ViewState<VM>() {
        override fun setForeground(foregrounded: Boolean): ViewState<VM> {
            return if (foregrounded && value != null && attached) Active(value) else copy(foregrounded = true)
        }
        override fun setAttached(attached: Boolean): ViewState<VM> {
            return if (attached && value != null && foregrounded) Active(value) else copy(attached = true)
        }

        override fun setVM(value: VM?): ViewState<VM> {
            return if (value != null && foregrounded && attached) Active(value) else copy(value = value)
        }
    }
}
