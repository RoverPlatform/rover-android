/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.experiences.classic.concerns

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
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
    private val cancellationBlock: (() -> Unit)? = null,
    private val binding: (viewModel: VM?, subscriptionCallback: (Subscription) -> Unit) -> Unit
) {
    private var outstandingSubscriptions: List<Subscription>? = null

    private var viewState: ViewState<VM> = ViewState(true, null)
        set(value) {
            val oldValue = field
            field = value
            when {
                value.foregrounded && value.viewModel != null && oldValue != value -> invokeBinding(value.viewModel)
                !value.foregrounded || value.viewModel == null && oldValue != value -> cancelSubscriptions()
            }
        }

    init {
        setupViewLifecycleObserver(view)
    }

    /**
     * Called when a view model bound
     */
    operator fun setValue(thisRef: Any, property: KProperty<*>, value: VM?) {
        if (viewState.viewModel != null && !rebindingAllowed) throw RuntimeException("This view does not support being re-bound to a new view model.")
        viewState = viewState.setVM(null)
        viewState = viewState.setVM(value)
    }

    operator fun getValue(thisRef: Any, property: KProperty<*>) = viewState.viewModel

    private fun cancelSubscriptions() {
        // cancel any existing async subscriptions.
        outstandingSubscriptions?.forEach { subscription -> subscription.cancel() }
        outstandingSubscriptions = null
        cancellationBlock?.invoke()
    }

    private fun invokeBinding(value: VM?) {
        binding(value) { subscription: Subscription ->
            if (viewState.viewModel == value) {
                // a subscription has come alive for currently active view model!
                outstandingSubscriptions = listOf(subscription) + (outstandingSubscriptions ?: listOf())
            } else {
                // subscription for a stale view model has come up.  cancel it immediately.
                subscription.cancel()
            }
        }
    }

    val subscription: Subscription? = null

    private fun setupViewLifecycleObserver(view: View?) {
        ((view?.context) as? LifecycleOwner)?.lifecycle?.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            fun paused() {
                viewState = viewState.setForeground(false)
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
            fun resumed() {
                viewState = viewState.setForeground(true)
            }
        })
    }
}

data class ViewState<VM : Any>(val foregrounded: Boolean, val viewModel: VM?) {
    fun setForeground(foregrounded: Boolean) = copy(foregrounded = foregrounded)
    fun setVM(viewModel: VM?) = copy(viewModel = viewModel)
}
