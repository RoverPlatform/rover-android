package io.rover.core.streams

import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

/**
 * These methods create a new [Publisher] from scratch.  Often used as the root of a publisher
 * chain.
 */
object Publishers {
    fun <T> just(item: T): Publisher<T> {
        return Publisher { subscriber ->
            var completed = false
            subscriber.onSubscribe(
                object : Subscription {
                    override fun request(n: Long) {
                        if (n != Long.MAX_VALUE) {
                            throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")
                        }
                        if (completed) return
                        subscriber.onNext(item)
                        subscriber.onComplete()
                        completed = true
                    }

                    override fun cancel() { /* this yields immediately, cancel can have no effect */ }
                }
            )
        }
    }

    fun <T> error(error: Throwable): Publisher<T> {
        return Publisher { subscriber ->
            subscriber.onSubscribe(
                object : Subscription {
                    override fun cancel() { /* this yields immediately, cancel can have no effect */ }

                    override fun request(n: Long) {
                        if (n != Long.MAX_VALUE) {
                            throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")
                        }
                        subscriber.onError(error)
                    }
                }
            )
        }
    }

    fun <T> empty(): Publisher<T> {
        return Publisher { s ->
            s.onSubscribe(object : Subscription {
                override fun request(n: Long) { /* this completes immediately, backpressure can have no effect */ }

                override fun cancel() { /* this completes immediately, cancel can have no effect */ }
            })
            s.onComplete()
        }
    }

    /**
     * Emit the signals from two or more [Publisher]s without interleaving them.  That is,
     * it will only subscribe to subsequent [sources] (as ordered in the varargs) once prior
     * ones have completed.
     *
     * [concat()](http://reactivex.io/documentation/operators/concat.html).
     */
    fun <T> concat(vararg sources: Publisher<out T>): Publisher<T> {
        return Publisher { subscriber ->
            var cancelled = false
            var requested = false

            val subscription = object : Subscription {
                override fun cancel() {
                    cancelled = true
                }

                override fun request(n: Long) {
                    if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")

                    if (requested) return
                    requested = true
                    // subscribe to first thing, wait for it to complete, then subscribe to second thing, wait for it to complete, etc.
                    fun recursiveSubscribe(remainingSources: List<Publisher<out T>>) {
                        if (remainingSources.isEmpty()) {
                            subscriber.onComplete()
                        } else {
                            // subscribe to the source:
                            remainingSources.first().subscribe(object : Subscriber<T> {
                                override fun onComplete() {
                                    // While there is potentially a risk of a stack overflow here if
                                    // all the sources emit onSubscribe synchronously, but in
                                    // practical terms, there will not be that many sources given to
                                    // concat().
                                    if (!cancelled) recursiveSubscribe(
                                        remainingSources.subList(1, remainingSources.size)
                                    )
                                }

                                override fun onError(error: Throwable) {
                                    if (!cancelled) subscriber.onError(error)
                                }

                                override fun onNext(item: T) {
                                    if (!cancelled) subscriber.onNext(item)
                                }

                                override fun onSubscribe(subscription: Subscription) {
                                    subscription.request(Long.MAX_VALUE)
                                    /* no-op: we don't tell our subscribers about each source subscribing */
                                }
                            })
                        }
                    }

                    recursiveSubscribe(sources.asList())
                }
            }

            subscriber.onSubscribe(subscription)
        }
    }

    /**
     * Emit the signals from two or more [Publisher]s while interleaving them.  It will
     * subscribe to all of the [sources] when subscribed to.
     *
     * [merge()](http://reactivex.io/documentation/operators/merge.html).
     */
    fun <T> merge(vararg sources: Publisher<out T>): Publisher<T> {
        return Publisher { subscriber ->
            var cancelled = false
            var requested = false

            val subscriptions: MutableSet<Subscription> = mutableSetOf()

            val subscription = object : Subscription {
                override fun cancel() {
                    cancelled = true
                    // cancel our subscriptions to all the sources
                    subscriptions.forEach { it.cancel() }
                }

                override fun request(n: Long) {
                    if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")

                    if (requested) return
                    requested = true
                    val remainingSources = sources.toMutableSet()

                    sources.forEach { source ->
                        source.subscribe(object : Subscriber<T> {
                            override fun onComplete() {
                                remainingSources.remove(source)
                                if (remainingSources.isEmpty() && !cancelled) {
                                    subscriber.onComplete()
                                }
                            }

                            override fun onError(error: Throwable) {
                                if (!cancelled) subscriber.onError(error)
                            }

                            override fun onNext(item: T) {
                                if (!cancelled) subscriber.onNext(item)
                            }

                            override fun onSubscribe(subscription: Subscription) {
                                if (!cancelled) {
                                    subscriptions.add(subscription)
                                    subscription.request(Long.MAX_VALUE)
                                } else {
                                    // just in case a subscription comes up after we are cancelled
                                    // ourselves, cancel.
                                    subscription.cancel()
                                }
                            }
                        })
                    }
                }
            }

            subscriber.onSubscribe(subscription)
        }
    }

    /**
     * When subscribed to & requested, evaluate the given [builder] block that will yield a
     * [Publisher] that is then subscribed to.
     *
     * Note that while [builder] method can block (provided you are subscribing your Publisher chain
     * on a thread/scheduler that you do not mind blocking).
     */
    fun <T> defer(builder: () -> Publisher<T>): Publisher<T> {
        return Publisher { subscriber -> builder().subscribe(subscriber) }
    }

    /**
     * When subscribed to & requested, the given [callable] is executed and given the subscriber.
     * From there it can call onNext()/onError() and so forth as it likes.
     */
    fun <T> create(callable: (subscriber: Subscriber<in T>) -> Unit): Publisher<T> {
        return Publisher { subscriber ->
            subscriber.onSubscribe(
                object : Subscription {
                    override fun cancel() {
                        // cancellation not supported.
                    }

                    override fun request(n: Long) {
                        if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")

                        callable(subscriber)
                    }
                }
            )
        }
    }

    fun <T, R> combineLatest(sources: List<Publisher<T>>, combiner: (List<T>) -> R): Publisher<R> {
        return Publisher { subscriber ->
            var cancelled = false
            var requested = false

            val latest = HashMap<Int, T>()

            val subscriptions: MutableSet<Subscription> = mutableSetOf()

            subscriber.onSubscribe(
                object : Subscription {
                    override fun cancel() {
                        cancelled = true
                        subscriptions.forEach { it.cancel() }
                    }

                    override fun request(n: Long) {
                        if (n != Long.MAX_VALUE) throw RuntimeException("Backpressure signalling not supported.  Request Long.MAX_VALUE.")

                        if (requested) return
                        requested = true
                        val remainingSources = sources.toMutableSet()

                        sources.forEachIndexed { index, source ->
                            source.subscribe(object : Subscriber<T> {
                                override fun onComplete() {
                                    remainingSources.remove(source)
                                    if (remainingSources.isEmpty() && !cancelled) {
                                        subscriber.onComplete()
                                    }
                                }

                                override fun onError(error: Throwable) {
                                    if (!cancelled) subscriber.onError(error)
                                }

                                override fun onNext(item: T) {
                                    if (!cancelled) {
                                        latest[index] = item
                                        // if we have a value for all the sources, run combiner and
                                        // then emit!
                                        if (latest.count() == sources.count()) {
                                            subscriber.onNext(
                                                combiner(
                                                    latest.keys.sorted().map { latest[it]!! }
                                                )
                                            )
                                        }
                                    }
                                }

                                override fun onSubscribe(subscription: Subscription) {
                                    if (!cancelled) {
                                        subscriptions.add(subscription)
                                        subscription.request(Long.MAX_VALUE)
                                    } else {
                                        // just in case a subscription comes up after we are cancelled
                                        // ourselves, cancel.
                                        subscription.cancel()
                                    }
                                }
                            })
                        }
                    }
                }
            )
        }
    }

    fun <T1, T2, R> combineLatest(
        source1: Publisher<T1>,
        source2: Publisher<T2>,
        combiner: (T1, T2) -> R
    ): Publisher<R> {
        @Suppress("UNCHECKED_CAST") // Suppression due to erasure/variance issues.
        return combineLatest(listOf(source1, source2) as List<Publisher<Any>>) { list: List<Any> ->
            // Suppression due to erasure.
            @Suppress("UNCHECKED_CAST")
            combiner(list[0] as T1, list[1] as T2)
        }
    }

    fun <T1, T2, T3, R> combineLatest(
        source1: Publisher<T1>,
        source2: Publisher<T2>,
        source3: Publisher<T3>,
        combiner: (T1, T2, T3) -> R
    ): Publisher<R> {
        @Suppress("UNCHECKED_CAST") // Suppression due to erasure/variance issues.
        return combineLatest(listOf(source1, source2, source3) as List<Publisher<Any>>) { list: List<Any> ->
            // Suppression due to erasure.
            @Suppress("UNCHECKED_CAST")
            combiner(list[0] as T1, list[1] as T2, list[2] as T3)
        }
    }
}