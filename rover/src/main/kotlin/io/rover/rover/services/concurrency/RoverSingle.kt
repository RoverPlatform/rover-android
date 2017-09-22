package io.rover.rover.services.concurrency

import java.util.*

// I think I can come up with a very basic "Rx-lite" here.

// Single.observeOn(subscriber, someSortOfRoverScheduler).

interface Subscription<T> {
    fun completed(value: T)

    fun error(error: Throwable)

    // TODO: technically needs unsubscription support.  but it's not a priority.
}

interface Scheduler {
    /**
     * Schedule some sort of synchronous operation (computation or blocking I/O) to happen
     * on this scheduler.  Returns a hot Single (that is, it will not wait until it is subscribed
     * to in order to begin the operation).
     */
    fun <T> scheduleOperation(operation: () -> T): Single<T>
}

interface Subscriber<T>: Subscription<T>

interface Single<T> {
    /**
     * Start observing this single.
     */
    fun subscribe(subscription: Subscription<T>, scheduler: Scheduler): Subscriber<T>

    /**
     * Start observing this single.
     */
    fun subscribe(
        completed: (T) -> Unit,
        error: (Throwable) -> Unit,
        scheduler: Scheduler
    ) {
        subscribe(object : Subscription<T> {
            override fun completed(value: T) {
                completed(value)
            }

            override fun error(error: Throwable) {
                error(error)
            }
        }, scheduler)
    }

    companion object {
        fun <T> just(value: T): Single<T> {
            return object : Subject<T>() {
                override fun subscribe(subscription: Subscription<T>, scheduler: Scheduler): Subscriber<T> {
                    val subscriber = super.subscribe(subscription, scheduler)
                    subscriber.completed(value)
                    return subscriber
                }
            }
        }
    }

    /**
     * Map the item emitted by this Single by way of a predicate.
     */
    fun <M> map(processingScheduler: Scheduler, predicate: (T) -> M): Single<M> {
        return object : Subject<M>() {
            override fun subscribe(subscription: Subscription<M>, scheduler: Scheduler): Subscriber<M> {
                val subscription = super.subscribe(subscription, scheduler)
                // now subscribe to the prior
                this@Single.subscribe(
                    object : Subscription<T> {
                        override fun completed(value: T) {
                            try {
                                subscription.completed(predicate(value))
                            } catch (e: Throwable) {
                                subscription.error(e)
                            }
                        }

                        override fun error(error: Throwable) {
                            subscription.error(error)
                        }
                    },
                    processingScheduler
                )
                return subscription
            }
        }
    }
}


open class Subject<T>: Single<T>, Subscriber<T> {
    private val subscriptions = Collections.synchronizedSet(mutableSetOf<Subscriber<T>>())

    override fun completed(value: T) {
        subscriptions.forEach { it.completed(value) }
    }

    override fun error(error: Throwable) {
        subscriptions.forEach { it.error(error) }
    }

    override fun subscribe(subscription: Subscription<T>, scheduler: Scheduler): Subscriber<T> {
        val subscriber = Subject<T>()
        subscriptions.add(
            subscriber
        )
        return subscriber
    }
}
