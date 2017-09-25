package io.rover.rover.services.concurrency

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks
import android.content.ComponentCallbacks2
import android.support.annotation.MainThread
import java.util.*
import java.util.concurrent.Callable

interface Subscription<T> {
    // TODO: technically needs unsubscription support.  but it's not a priority.
}

interface Scheduler {
    /**
     * Schedule some sort of synchronous operation (computation or blocking I/O) to happen
     * on this scheduler.  Returns a hot [Single] (that is, it will not wait until it is subscribed
     * to in order to begin the operation).
     */
    fun <T> scheduleOperation(operation: () -> T): Single<T>

    /**
     * Run a closure that delivers its result as a side-effect.  Meant for use by subscribers.
     */
    fun scheduleSideEffectOperation(operation: () -> Unit)
}

interface Subscriber<T>: Subscription<T> {
    fun onCompleted(value: T)

    fun onError(error: Throwable)
}

/**
 * An observable event source that can only emit one event (or, in lieu, an error).
 *
 * Can be subscribed to by a [Subscriber] (or just with two closures for convenience).
 */
interface Single<T> {
    /**
     * Listen to this asynchronous event source in the context of an Android activity with a
     * simple callback that will be called on the main thread when the result is ready.
     */
    fun call(
        callback: (T) -> Unit
    ) {
        // TODO: mainthreadscheduler should be injected.  Thankfully it's a light object to
        // construct.  These methods should be moved out of the
        // interface.
        // TODO: should probably have a story for lifecycle composition

        val mainThreadScheduler = MainThreadScheduler()
        subscribe(callback, { error -> throw error }, mainThreadScheduler)
    }

    /**
     * Start observing this single.  The returned subscriber will be executed on the given
     * scheduler.
     */
    fun subscribe(subscriber: Subscriber<T>, scheduler: Scheduler): Subscription<T>

    /**
     * Start observing this single.
     *
     * THIS MUST NOT BE OVERRIDDEN AND REPLACED TO ADD BEHAVIOUR
     *
     * TODO: this façade method should be factored out; having it overridable here has already caused 1 nasty bug.
     */
    fun subscribe(
        completed: (T) -> Unit,
        error: (Throwable) -> Unit,
        scheduler: Scheduler
    ): Subscription<T> {
        return subscribe(object : Subscriber<T> {
            override fun onCompleted(value: T) {
                completed(value)
            }

            override fun onError(error: Throwable) {
                error(error)
            }
        }, scheduler)
    }



    companion object {
        fun <T> just(value: T): Single<T> {
            return object : Subscribable<T>() {
                override fun subscribe(subscriber: Subscriber<T>, scheduler: Scheduler): Subscription<T> {
                    val subscription = super.subscribe(subscriber, scheduler)
                    subscriber.onCompleted(value)
                    return subscription
                }
            }
        }
    }

    /**
     * Map the item emitted by this Single by way of a predicate.
     */
    fun <M> map(processingScheduler: Scheduler, predicate: (T) -> M): Single<M> {
        val prior: Single<T> = this
        return object : Subscribable<M>() {
            override fun subscribe(subscriber: Subscriber<M>, scheduler: Scheduler): Subscription<M> {
                val subscription = super.subscribe(subscriber, scheduler)

                val subject = this
                // now subscribe to the prior on behalf of our subscriber
                prior.subscribe(
                    { value ->
                        try {
                            // and emit the prior's emission, after transforming it.
                            subject.onCompleted(
                                // apply the transform
                                predicate(value)
                            )
                        } catch (e: Throwable) {
                            subject.onError(e)
                        }
                    }, { error ->
                        subject.onError(error)
                    },
                    scheduler
                )
                return subscription
            }
        }
    }
}

/**
 * An implementation of [Subscriber] that implements maintaining a list of Subscribers.
 */
open class Subscribable<T>: Single<T>, Subscriber<T> {
    private val subscriptions = Collections.synchronizedSet(mutableSetOf<Pair<Subscriber<T>, Scheduler>>())

    override fun onCompleted(value: T) {
        subscriptions.forEach { it.second.scheduleSideEffectOperation { it.first.onCompleted(value) }  }
    }

    override fun onError(error: Throwable) {
        subscriptions.forEach { it.second.scheduleSideEffectOperation { it.first.onError(error) } }
    }

    override fun subscribe(subscriber: Subscriber<T>, scheduler: Scheduler): Subscription<T> {
        subscriptions.add(
            Pair(subscriber, scheduler)
        )

        // the subscriber itself is the subscription, for now.
        return subscriber
    }
}
