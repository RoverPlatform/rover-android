@file:JvmName("Operators")

package io.rover.core.streams

import android.os.Handler
import android.os.Looper
import io.rover.core.logging.log
import org.reactivestreams.Processor
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

fun <T> Publisher<out T>.subscribe(
    onNext: (item: T) -> Unit,
    onError: (throwable: Throwable) -> Unit,
    subscriptionReceiver: ((Subscription) -> Unit)? = null
) {
    this.subscribe(object : Subscriber<T> {
        override fun onComplete() { }

        override fun onError(error: Throwable) { onError(error) }

        override fun onNext(item: T) { onNext(item) }

        override fun onSubscribe(subscription: Subscription) {
            subscription.request(Long.MAX_VALUE)
            if (subscriptionReceiver != null) {
                subscriptionReceiver(subscription)
            }
        }
    })
}

fun <T> Publisher<T>.subscribe(onNext: (item: T) -> Unit) {
    this.subscribe(object : Subscriber<T> {
        override fun onComplete() { }

        override fun onError(error: Throwable) {
            throw RuntimeException("Undeliverable (unhandled) exception", error)
        }

        override fun onNext(item: T) { onNext(item) }

        override fun onSubscribe(subscription: Subscription) {
            subscription.request(Long.MAX_VALUE)
        }
    })
}

fun <T, R> Publisher<T>.map(transform: (T) -> R): Publisher<R> {
    val prior = this
    return Publisher { subscriber ->
        prior.subscribe(
            object : Subscriber<T> {
                override fun onComplete() {
                    subscriber.onComplete()
                }

                override fun onError(error: Throwable) {
                    subscriber.onError(error)
                }

                override fun onNext(item: T) {
                    val transformed = try {
                        transform(item)
                    } catch (error: Throwable) {
                        subscriber.onError(Exception("Transform failed in Publisher.map().", error))
                        return
                    }
                    subscriber.onNext(transformed)
                }

                override fun onSubscribe(subscription: Subscription) {
                    // for clarity, this is called when I (map()) have subscribed
                    // successfully to the source.  I then want to let the downstream
                    // consumer know that I have subscribed successfully on their behalf,
                    // and also allow them to pass cancellation through.
                    val consumerSubscription = object : Subscription {
                        override fun cancel() { subscription.cancel() }
                        override fun request(n: Long) {
                            if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")
                            subscription.request(Long.MAX_VALUE)
                        }
                    }

                    subscriber.onSubscribe(consumerSubscription)
                }
            }
        )
    }
}

fun <T> Publisher<T>.filter(predicate: (T) -> Boolean): Publisher<T> {
    return Publisher { subscriber ->
        this@filter.subscribe(object : Subscriber<T> {
            override fun onComplete() {
                subscriber.onComplete()
            }

            override fun onError(error: Throwable) {
                subscriber.onError(error)
            }

            override fun onNext(item: T) {
                if (predicate(item)) subscriber.onNext(item)
            }

            override fun onSubscribe(subscription: Subscription) {
                val consumerSubscription = object : Subscription {
                    override fun cancel() {
                        subscription.cancel()
                    }

                    override fun request(n: Long) {
                        if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")
                        subscription.request(Long.MAX_VALUE)
                    }
                }

                subscriber.onSubscribe(consumerSubscription)
            }
        })
    }
}

/**
 * Use this transformation if you have a Publisher of a given type, and you wish to filter it down
 * to only elements of a given subtype.
 *
 * TODO: warn if T is Any, because that probably means consumer is using this transform on a
 * stream with a badly inferred type and thus all of their events could be an unexpected type
 * that will be ignored.
 */
@Suppress("UNCHECKED_CAST") // Suppression because of variance issues.
inline fun <reified TSub : T, reified T : Any> Publisher<T>.filterForSubtype(): Publisher<TSub> {
    return this.filter { TSub::class.java.isAssignableFrom(it::class.java) } as Publisher<TSub>
}

fun <T, R> Publisher<T>.flatMap(transform: (T) -> Publisher<out R>): Publisher<R> {
    val prior = this
    return Publisher { subscriber ->
        val outstanding: ConcurrentHashMap<Subscriber<*>, Boolean> = ConcurrentHashMap()

        fun informSubscriberCompleteIfAllCompleted() {
            // TODO: wait for all waiting transform subscriptions to complete too.
            if (outstanding.isEmpty()) {
                subscriber.onComplete()
            }
        }

        val sourceSubscriber = object : Subscriber<T> {
            override fun onComplete() {
                outstanding.remove(this)
                informSubscriberCompleteIfAllCompleted()
            }

            override fun onError(error: Throwable) {
                subscriber.onError(error)
            }

            override fun onNext(item: T) {
                val transformPublisher = try {
                    transform(item)
                } catch (error: Throwable) {
                    subscriber.onError(Exception("Transform failed in Publisher.flatMap().", error))
                    return
                }

                val transformSubscriber = object : Subscriber<R> {
                    override fun onComplete() {
                        outstanding.remove(this)
                        informSubscriberCompleteIfAllCompleted()
                    }

                    override fun onError(error: Throwable) {
                        subscriber.onError(error)
                    }

                    override fun onNext(item: R) {
                        subscriber.onNext(item)
                    }

                    override fun onSubscribe(subscription: Subscription) {
                        if(outstanding[this] == true) {
                            // only pass through subscription if not yet onComplete'd.
                            subscription.request(Long.MAX_VALUE)
                        }
                    }
                }
                outstanding[transformSubscriber] = true
                transformPublisher.subscribe(transformSubscriber)
            }

            override fun onSubscribe(subscription: Subscription) {
                // for clarity, this is called when I (flatMap()) have subscribed
                // successfully to the source.  I then want to let the downstream
                // consumer know that I have subscribed successfully on their behalf,
                // and also allow them to pass cancellation through.
                val subscriberSubscription = object : Subscription {
                    override fun cancel() { subscription.cancel() }
                    override fun request(n: Long) {
                        if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")
                        subscription.request(Long.MAX_VALUE)
                    }
                }

                subscriber.onSubscribe(subscriberSubscription)
            }
        }

        // we also want to wait for our sourceSubscriber to complete before notifying
        // our subscribers that we have
        outstanding[sourceSubscriber] = true

        prior.subscribe(sourceSubscriber)
    }
}

/**
 * Subscribe to the [Publisher] once, and multicast yielded signals to multiple subscribers.
 *
 * AKA Tee.
 *
 * Note that [share] will subscribe to the source once the first consumer subscribes to it.
 *
 * NB. This operator is not yet thread safe.
 */
fun <T> Publisher<T>.share(): Publisher<T> {
    val multicastTo: MutableSet<Subscriber<in T>> = mutableSetOf()

    var subscribedToSource = false

    return Publisher { subscriber ->
        val consumerSubscription = object : Subscription {
            override fun request(n: Long) {
                if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")

                multicastTo.add(subscriber)

                // subscribe on initial.
                if (!subscribedToSource) {
                    subscribedToSource = true
                    this@share.subscribe(
                        object : Subscriber<T> {
                            override fun onComplete() {
                                multicastTo.forEach { it.onComplete() }
                                multicastTo.clear()
                            }

                            override fun onError(error: Throwable) {
                                multicastTo.forEach { it.onError(error) }
                            }

                            override fun onNext(item: T) {
                                multicastTo.forEach { it.onNext(item) }
                            }

                            override fun onSubscribe(subscription: Subscription) {
                                subscription.request(Long.MAX_VALUE)
                            }
                        }
                    )
                }
            }

            override fun cancel() {
                // he wants out
                multicastTo.remove(subscriber)

                // TODO: once the last subscriber has departed we should unsubscribe the source.
                // see comments in onSubscribe below.
            }
        }

        subscriber.onSubscribe(consumerSubscription)
    }
}

/**
 * Similar to [shareAndReplay], but in addition buffering and re-emitting the [count] most recent
 * events to any new subscriber, it will also immediately subscribe to the source and begin
 * buffering.  This is suitable for use with hot observables.
 *
 * NB. This operator is not yet thread safe.
 */
fun <T> Publisher<T>.shareHotAndReplay(count: Int): Publisher<T> {
    val buffer = ArrayDeque<T>(count)

    val multicastTo: MutableSet<Subscriber<in T>> = mutableSetOf()

    this.subscribe(
        object : Subscriber<T> {
            override fun onComplete() {
                multicastTo.forEach { it.onComplete() }
                multicastTo.clear()
            }

            override fun onError(error: Throwable) {
                multicastTo.forEach { it.onError(error) }
            }

            override fun onNext(item: T) {
                multicastTo.forEach { it.onNext(item) }
                buffer.addLast(item)
                // emulate a ring buffer by removing any older entries than `count`
                for (i in 1..buffer.size - count) {
                    buffer.removeFirst()
                }
            }

            override fun onSubscribe(subscription: Subscription) {
                subscription.request(Long.MAX_VALUE)
            }
        }
    )

    return Publisher { subscriber ->
        val subscription = object : Subscription {
            override fun request(n: Long) {
                if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")
                multicastTo.add(subscriber)
                // catch up the new subscriber on the `count` number of last events.
                buffer.forEach { event -> subscriber.onNext(event) }
            }

            override fun cancel() {
                // he wants out
                multicastTo.remove(subscriber)
            }
        }
        subscriber.onSubscribe(subscription)
    }
}

/**
 * Similar to [share], but will buffer and re-emit the [count] most recent events to any new
 * subscriber.  Similarly to [share], it will not subscribe to the source until it is first
 * subscribed to itself.
 *
 * @param count the maximum amount of emissions to buffer.  If 0, an infinite number will be
 * buffered.
 *
 * This appears somewhat equivalent to Jake Wharton's
 * [RxReplayingShare](https://github.com/JakeWharton/RxReplayingShare).
 *
 * Not thread safe.
 */
fun <T> Publisher<T>.shareAndReplay(count: Int): Publisher<T> {
    val buffer = ArrayDeque<T>(count)

    // this set is of subscribers that subscribed before we ourselves managed to complete
    // to the prior publisher.
    val subscribeTo: MutableSet<Subscriber<in T>> = mutableSetOf()

    val multicastTo: MutableSet<Subscriber<in T>> = mutableSetOf()

    var subscribing = false
    var requested = false
    var sourceSubscription: Subscription? = null

    fun subscribeSubscriber(subscriber: Subscriber<in T>) {
        subscriber.onSubscribe(
            object : Subscription {
                override fun request(n: Long) {
                    if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")
                    multicastTo.add(subscriber)
                    buffer.forEach { subscriber.onNext(it) } // bring subscriber up to date with prior items
                    if (requested) return
                    requested = true
                    sourceSubscription!!.request(Long.MAX_VALUE)
                }

                override fun cancel() {
                    // he wants out
                    multicastTo.remove(subscriber)

                    if (multicastTo.isEmpty()) {
                        sourceSubscription?.cancel()
                        subscribing = false
                    }
                }
            }
        )
    }

    fun subscribeToSource() {
        subscribing = true
        this.subscribe(
            object : Subscriber<T> {
                override fun onComplete() {
                    multicastTo.forEach { it.onComplete() }
                    multicastTo.clear()
                }

                override fun onError(error: Throwable) {
                    multicastTo.forEach { it.onError(error) }
                }

                override fun onNext(item: T) {
                    multicastTo.forEach { it.onNext(item) }
                    buffer.addLast(item)
                    // emulate a ring buffer by removing any older entries than `count`
                    if (count != 0) {
                        for (i in 1..buffer.size - count) {
                            buffer.removeFirst()
                        }
                    }
                }

                override fun onSubscribe(subscription: Subscription) {
                    sourceSubscription = subscription
                    subscribing = false
                    subscribeTo.forEach { subscriber ->
                        subscribeSubscriber(subscriber)
                    }
                }
            }
        )
    }

    return Publisher { subscriber ->
        // subscribe to source on initial subscribe.
        if (sourceSubscription == null && !subscribing) {
            subscribeTo.add(subscriber)
            subscribeToSource()
        } else if (sourceSubscription == null) {
            // we're waiting on the source to subscribe.  this will add it to the multicast list and
            // wait.
            subscribeTo.add(subscriber)
        } else {
            // we can give them their subscription right away
            subscribeSubscriber(subscriber)
        }
    }
}

@Suppress("UNCHECKED_CAST") // Suppression because of variance issues.
fun <T : Any> Publisher<T>.first(): Publisher<T> {
    return Publisher { subscriber ->
        var sourceSubscription: Subscription? = null

        this@first.subscribe(
            object : Subscriber<T> by subscriber as Subscriber<T> {
                override fun onComplete() {
                    subscriber.onComplete()
                    sourceSubscription = null
                }

                override fun onNext(item: T) {
                    // on first item unsubscribe and complete.
                    subscriber.onNext(item)
                    subscriber.onComplete()
                    sourceSubscription?.cancel()
                    sourceSubscription = null
                }

                override fun onSubscribe(subscription: Subscription) {
                    if (sourceSubscription != null) {
                        throw RuntimeException("first() already subscribed to.")
                    }
                    sourceSubscription = subscription
                    subscriber.onSubscribe(
                        object : Subscription {
                            override fun request(n: Long) {
                                if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")
                                subscription.request(Long.MAX_VALUE)
                            }

                            override fun cancel() {
                                subscription.cancel()
                            }
                        }
                    )
                }
            }
        )
    }
}

/**
 * Will filter out sequences of identitical (by comparison) items.  An item will not be
 * emitted if it is the same as the prior.
 */
fun <T : Any> Publisher<T>.distinctUntilChanged(): Publisher<T> {
    return Publisher { subscriber ->

        var lastSeen: LastSeen<T> = LastSeen.NoneYet()
        var sourceSubscriber: Subscriber<T>?

        subscriber.onSubscribe(
            object : Subscription {
                override fun cancel() {
                    // TODO gotta pass subscription through
                }

                override fun request(n: Long) {
                    // subscribe to prior
                    @Suppress("UNCHECKED_CAST") // suppressed due to erasure/variance issues.
                    sourceSubscriber = object : Subscriber<T> by subscriber as Subscriber<T> {
                        override fun onNext(item: T) {
                            // atomically copy lastSeen so smart cast below can work.
                            val lastSeenCaptured = lastSeen
                            when (lastSeenCaptured) {
                                is LastSeen.NoneYet -> {
                                    subscriber.onNext(item)
                                }
                                is LastSeen.Seen<T> -> {
                                    if (lastSeenCaptured.value != item) {
                                        subscriber.onNext(item)
                                    }
                                }
                            }
                            lastSeen = LastSeen.Seen(item)
                        }
                    }

                    this@distinctUntilChanged.subscribe(
                        sourceSubscriber!!
                    )
                }
            }
        )
    }
}

/**
 * A maybe type just because our maybe value itself could be null.
 */
sealed class LastSeen<T : Any> {
    class NoneYet<T : Any> : LastSeen<T>()
    class Seen<T : Any>(val value: T) : LastSeen<T>()
}

/**
 * Subscribes to the source, stores and re-emit the latest item seen of each of the types to any
 * new subscriber.  Note that this is vulnerable to the typical Java/Android platform issue of
 * type erasure, so do not try to register different parameterizations of the same type.
 *
 * Note that any re-emitted items are emitted in the order of the [types] given.
 *
 * Not thread safe.
 */
fun <T : Any> Publisher<out T>.shareAndReplayTypesOnResubscribe(vararg types: Class<out T>): Publisher<T> {
    val lastSeen: MutableMap<Class<out T>, T?> = types.associate { Pair(it, null) }.toMutableMap()

    val shared = this.share()

    return Publisher { subscriber ->
        var requested = false
        shared.subscribe(
            object : Subscriber<T> {
                override fun onComplete() {
                    subscriber.onComplete()
                }

                override fun onError(error: Throwable) {
                    subscriber.onError(error)
                }

                override fun onNext(item: T) {
                    subscriber.onNext(item)
                    // TODO this has a problem: it does not check for descendant classes, it
                    // must be an exact match.

                    // this will actually be called for every existing subscriber.  thankfully,
                    // setting the lastSeen is an idempotent operation, so it's pretty harmless
                    // to do needless repeats of.
                    if (lastSeen.keys.contains(item.javaClass)) {
                        lastSeen[item.javaClass] = item
                    }
                }

                override fun onSubscribe(subscription: Subscription) {
                    val consumerSubscription = object : Subscription {
                        override fun cancel() {
                            subscription.cancel()
                        }

                        override fun request(n: Long) {
                            if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")
                            if (requested) return
                            requested = true
                            subscription.request(Long.MAX_VALUE)
                            lastSeen.values.filterNotNull().forEach {
                                subscriber.onNext(it)
                            }
                        }
                    }

                    subscriber.onSubscribe(consumerSubscription)
                }
            }
        )
    }
}

@Suppress("UNCHECKED_CAST") // Suppression because of variance issues.
fun <T> Publisher<T>.doOnSubscribe(behaviour: () -> Unit): Publisher<T> {
    return Publisher { subscriber ->
        val wrappedSubscriber = object : Subscriber<T> by subscriber as Subscriber<T> {
            override fun onSubscribe(subscription: Subscription) {
                subscriber.onSubscribe(subscription)
                behaviour()
            }
        }
        this@doOnSubscribe.subscribe(wrappedSubscriber)
    }
}

@Suppress("UNCHECKED_CAST") // Suppression because of variance issues.
fun <T> Publisher<T>.doOnRequest(behaviour: () -> Unit): Publisher<T> {
    return Publisher { subscriber ->
        val wrappedSubscriber = object : Subscriber<T> by subscriber as Subscriber<T> {
            override fun onSubscribe(subscription: Subscription) {
                val consumerSubscription = object : Subscription {
                    override fun cancel() {
                        subscription.cancel()
                    }

                    override fun request(n: Long) {
                        subscription.request(n)
                        behaviour()
                    }
                }

                subscriber.onSubscribe(consumerSubscription)
            }
        }
        this@doOnRequest.subscribe(wrappedSubscriber)
    }
}

/**
 * Execute the given block when the subscription is cancelled.
 */
@Suppress("UNCHECKED_CAST") // Warning suppression needed because of variance issues.
fun <T> Publisher<T>.doOnUnsubscribe(behaviour: () -> Unit): Publisher<T> {
    return Publisher { subscriber ->
        val wrappedSubscriber = object : Subscriber<T> by subscriber as Subscriber<T> {
            override fun onSubscribe(subscription: Subscription) {
                val wrappedSubscription = object : Subscription {
                    override fun request(n: Long) {
                        if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")
                        subscription.request(n)
                    }

                    override fun cancel() {
                        behaviour()
                        subscription.cancel()
                    }
                }

                subscriber.onSubscribe(wrappedSubscription)
            }

            override fun onComplete() {
                behaviour()
                subscriber.onComplete()
            }
        }

        this@doOnUnsubscribe.subscribe(wrappedSubscriber)
    }
}

interface Subject<T> : Processor<T, T>

/**
 * Simultaneously a [Subscriber] and a [Publisher] at the same time.  Thus allows you to encapsulate
 * an external event source into a [Publisher].
 *
 * Supports multiple [Subscriber]s.
 */
class PublishSubject<T> : Subject<T> {
    private var subscribers: MutableSet<Subscriber<in T>> = mutableSetOf()

    override fun subscribe(subscriber: Subscriber<in T>) {
        val subscription = object : Subscription {
            override fun request(n: Long) {
                if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")
                // wire up the subscriber now that it has requested items.
                subscribers.add(subscriber)
            }

            override fun cancel() {
                subscribers.remove(subscriber)
            }
        }
        subscriber.onSubscribe(subscription)
    }

    override fun onComplete() {
        subscribers.forEach { it.onComplete() }
        subscribers.clear()
    }

    override fun onError(error: Throwable) {
        subscribers.forEach { it.onError(error) }
    }

    override fun onSubscribe(subscription: Subscription) {
        // when subscribed to something PublishSubject will just request immediately.
        subscription.request(Long.MAX_VALUE)
    }

    override fun onNext(item: T) {
        subscribers.forEach { it.onNext(item) }
    }
}

/**
 * Mirrors the source Publisher, but yields an error in the event that the given timeout runs
 * out before the source Publisher emits at least one item.
 *
 * Not thread safe, and uses Android static API (the main looper) and thus assumes subscription
 * and emission on the Android main thread.  Thus will break tests.
 */
@Suppress("UNCHECKED_CAST") // Suppression because of variance issues.
fun <T> Publisher<T>.timeout(interval: Long, unit: TimeUnit): Publisher<T> {

    class TimeoutPublisher : Publisher<T> {
        @Volatile
        var stillWaiting = true
        @Volatile
        var requested = false

        override fun subscribe(subscriber: Subscriber<in T>) {
            this@timeout.subscribe(
                object : Subscriber<T> by subscriber as Subscriber<T> {
                    override fun onComplete() {
                        stillWaiting = false
                        subscriber.onComplete()
                    }

                    override fun onNext(item: T) {
                        stillWaiting = false
                        log.v("Set stillWaiting to $stillWaiting")
                        subscriber.onNext(item)
                    }

                    override fun onError(error: Throwable) {
                        stillWaiting = false
                        subscriber.onError(error)
                    }

                    override fun onSubscribe(subscription: Subscription) {
                        val handler = Handler(Looper.getMainLooper())
                        val timeoutHandler = {
                            if (stillWaiting) {
                                // timeout has run out!
                                onError(Throwable("$interval ${unit.name.toLowerCase()} timeout has expired."))
                                subscription.cancel()
                            }
                        }

                        val clientSubscription = object : Subscription {
                            override fun cancel() {

                                // cancel the source:
                                subscription.cancel()

                                // cancel the timer:
                                stillWaiting = false

                                handler.removeCallbacks(timeoutHandler)
                            }

                            override fun request(n: Long) {
                                subscription.request(n)

                                if (requested) return
                                requested = true

                                handler.postDelayed(timeoutHandler, unit.toMillis(interval))
                            }
                        }

                        subscriber.onSubscribe(clientSubscription)
                    }
                }
            )
        }
    }

    return TimeoutPublisher()
}

fun <T> Collection<T>.asPublisher(): Publisher<T> {
    return Publisher { subscriber ->
        var requested = false
        val subscription = object : Subscription {
            override fun request(n: Long) {
                if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")
                if (requested) return
                requested = true
                this@asPublisher.forEach { item -> subscriber.onNext(item) }
                subscriber.onComplete()
            }

            override fun cancel() { /* we synchronously emit all (no backpressure), thus cancel is no-op */ }
        }
        subscriber.onSubscribe(subscription)
    }
}

/**
 * Execute a side-effect whenever an item is emitted by the Publisher.
 */
@Suppress("UNCHECKED_CAST") // Suppression because of variance issues.
fun <T> Publisher<T>.doOnNext(callback: (item: T) -> Unit): Publisher<T> {
    val prior = this
    return Publisher { subscriber ->
        prior.subscribe(
            object : Subscriber<T> by subscriber as Subscriber<T> {
                override fun onNext(item: T) {
                    callback(item)
                    subscriber.onNext(item)
                }

                override fun onSubscribe(subscription: Subscription) {
                    // 1:1 subscription semantics, so just pass it through.
                    subscriber.onSubscribe(subscription)
                }
            }
        )
    }
}

/**
 * Execute a side-effect whenever an error is emitted by the Publisher.
 */
@Suppress("UNCHECKED_CAST") // Suppression because of variance issues.
fun <T> Publisher<T>.doOnError(callback: (error: Throwable) -> Unit): Publisher<T> {
    val prior = this
    return Publisher { subscriber ->
        prior.subscribe(
            object : Subscriber<T> by subscriber as Subscriber<T> {
                override fun onError(error: Throwable) {
                    callback(error)
                    subscriber.onError(error)
                }

                override fun onSubscribe(subscription: Subscription) {
                    // 1:1 subscription semantics, so just pass it through.
                    subscriber.onSubscribe(subscription)
                }
            }
        )
    }
}

/**
 * Execute a side-effect whenever when the Publisher completes.
 */
@Suppress("UNCHECKED_CAST") // Suppression needed because of variance issues.
fun <T> Publisher<T>.doOnComplete(callback: () -> Unit): Publisher<T> {
    val prior = this
    return Publisher { subscriber ->
        prior.subscribe(
            object : Subscriber<T> by subscriber as Subscriber<T> {
                override fun onSubscribe(subscription: Subscription) {
                    // 1:1 subscription semantics, so just pass it through.
                    subscriber.onSubscribe(subscription)
                }

                override fun onComplete() {
                    callback()
                    subscriber.onComplete()
                }
            }
        )
    }
}

/**
 * Transform any emitted errors into in-band values.
 */
@Suppress("UNCHECKED_CAST") // Suppression because of variance issues.
fun <T, R> Publisher<T>.onErrorReturn(callback: (throwable: Throwable) -> R): Publisher<R> {
    val prior = this
    return Publisher { subscriber ->
        prior.subscribe(object : Subscriber<T> by subscriber as Subscriber<T> {
            override fun onError(error: Throwable) {
                subscriber.onNext(callback(error))
            }
        })
    }
}

// TODO: At such time as we set Android Min SDK to at least 24, change to use Optional here and at
// the usage sites instead (on account of the Reactive Streams spec not actually allowing for
// nulls, although the Java interfaces do allow for them at the moment).
fun <T> Publisher<T?>.filterNulls(): Publisher<T> = filter { it != null }.map { it!! }

/**
 * Republish emissions from the Publisher until such time as the provider [Publisher] given as
 * [stopper] emits completion, error, or an emission.
 */
fun <T, S> Publisher<T>.takeUntil(stopper: Publisher<S>): Publisher<T> {
    return Publisher { subscriber ->

        var stopperSubscription: Subscription? = null

        this@takeUntil.subscribe(object : Subscriber<T> {
            override fun onComplete() {
                subscriber.onComplete()
                stopperSubscription?.cancel()
                stopperSubscription = null
            }

            override fun onError(error: Throwable) {
                subscriber.onError(error)
                stopperSubscription?.cancel()
                stopperSubscription = null
            }

            override fun onNext(item: T) {
                subscriber.onNext(item)
            }

            override fun onSubscribe(subscription: Subscription) {
                // subscribe to the stopper and cancel the subscription whenever it emits
                // anything.
                stopper.subscribe(object : Subscriber<S> {
                    override fun onComplete() {
                        subscriber.onComplete()
                        subscription.cancel()
                        stopperSubscription = null
                    }

                    override fun onError(error: Throwable) {
                        subscriber.onError(error)
                        subscription.cancel()
                        stopperSubscription = null
                    }

                    override fun onNext(item: S) {
                        subscription.cancel()
                        stopperSubscription?.cancel()
                        stopperSubscription = null
                    }

                    override fun onSubscribe(receivedStopperSubscription: Subscription) {
                        // now that both the upstream and the stopper are subscribed we can let
                        // downstream know.
                        stopperSubscription = receivedStopperSubscription
                        subscriber.onSubscribe(object : Subscription {
                            override fun cancel() {
                                // downstream has cancelled, stop the stopper:
                                receivedStopperSubscription.cancel()
                                stopperSubscription = null
                                subscription.cancel()
                            }

                            override fun request(n: Long) {
                                if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")
                                receivedStopperSubscription.request(Long.MAX_VALUE)
                                subscription.request(Long.MAX_VALUE)
                            }
                        })
                    }
                })
            }
        })
    }
}

/**
 * This will subscribe to Publisher `this` when it is subscribed to itself.  It will execute
 * subscription on the given executor.
 */
fun <T> Publisher<T>.subscribeOn(executor: Executor): Publisher<T> {
    return Publisher { subscriber ->
        executor.execute {
            // TODO: should we run unsubsriptions on the executor as well?

            // subscriber is the downstream/client subscriber.  it's waiting for a subscription.
            // however, rather than subscribing it right through to the parent publisher, we need to
            // intercede to wrap the subscription.

            @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
            this@subscribeOn.subscribe(
                object : Subscriber<T> by subscriber as Subscriber<T> {
                    override fun onSubscribe(subscription: Subscription) {
                        subscriber.onSubscribe(
                            object : Subscription {
                                override fun cancel() {
                                    // pass through to parent cancellation.
                                    executor.execute {
                                        subscription.cancel()
                                    }
                                }

                                override fun request(n: Long) {
                                    executor.execute {
                                        subscription.request(n)
                                    }
                                }
                            }
                        )
                    }
                }
            )
        }
    }
}

/**
 * This will subscribe to Publisher `this` when it is subscribed to itself.  It will execute
 * subscription on the given executor.
 */
fun <T> Publisher<T>.subscribeOn(scheduler: Scheduler): Publisher<T> {
    return Publisher { subscriber ->
        scheduler.execute {
            // TODO: should we run unsubscriptions on the executor as well?

            // subscriber is the downstream/client subscriber.  it's waiting for a subscription.
            // however, rather than subscribing it right through to the parent publisher, we need to
            // intercede to wrap the subscription.

            this@subscribeOn.subscribe(
                @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
                object : Subscriber<T> by subscriber as Subscriber<T> {
                    override fun onSubscribe(subscription: Subscription) {
                        subscriber.onSubscribe(
                            object : Subscription {
                                override fun cancel() {
                                    scheduler.execute {
                                        subscription.cancel()
                                    }
                                }

                                override fun request(n: Long) {
                                    scheduler.execute {
                                        subscription.request(n)
                                    }
                                }
                            }
                        )
                    }
                }
            )
        }
    }
}

/**
 * This will subscribe to Publisher `this` when it is subscribed to itself.  It will deliver all
 * callbacks to the subscribing Publisher on the given [executor].
 *
 * Note that the thread you call .subscribe() on remains important: be sure all subscriptions to set
 * up a given Publisher chain are all on a single thread.  Use
 */
fun <T> Publisher<T>.observeOn(executor: Executor): Publisher<T> {
    return Publisher { subscriber ->
        this@observeOn.subscribe(object : Subscriber<T> {
            override fun onComplete() {
                executor.execute {
                    subscriber.onComplete()
                }
            }

            override fun onError(error: Throwable) {
                executor.execute {
                    subscriber.onError(error)
                }
            }

            override fun onNext(item: T) {
                executor.execute {
                    subscriber.onNext(item)
                }
            }

            override fun onSubscribe(subscription: Subscription) {
                executor.execute {
                    subscriber.onSubscribe(subscription)
                }
            }
        })
    }
}

/**
 * Block the thread waiting for the publisher to complete.
 *
 * All emitted items are buffered into a list that is then returned.
 */
fun <T> Publisher<T>.blockForResult(
    timeoutSeconds: Int = 10,
    afterSubscribe: () -> Unit = {}
): List<T> {
    val latch = CountDownLatch(1)
    var receivedError: Throwable? = null
    val results: MutableList<T> = mutableListOf()

    this.subscribe(object : Subscriber<T> {
        override fun onComplete() {
            latch.countDown()
        }

        override fun onError(error: Throwable) {
            receivedError = error
            latch.countDown()
        }

        override fun onNext(item: T) {
            results.add(item)
        }

        override fun onSubscribe(subscription: Subscription) {
            afterSubscribe()
            subscription.request(Long.MAX_VALUE)
        }
    })

    if (!latch.await(timeoutSeconds.toLong(), TimeUnit.SECONDS)) {
        throw Exception("Reached timeout while blocking for publisher! Items received: ${results.count()}")
    }

    if (receivedError != null) {
        throw Exception("Error while blocking on Publisher.  Items received: ${results.count()}", receivedError)
    }

    return results
}