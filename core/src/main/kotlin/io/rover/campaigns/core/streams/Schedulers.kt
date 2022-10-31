@file:JvmName("Schedulers")

package io.rover.campaigns.core.streams

import android.os.Handler
import android.os.Looper
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.Executor

interface Scheduler {
    fun execute(runnable: () -> Unit)

    companion object
}

/**
 * Generate a [Scheduler] for the Android main thread/looper.
 */
fun Scheduler.Companion.forAndroidMainThread(): Scheduler {
    val handler = Handler(Looper.getMainLooper())
    return object : Scheduler {
        override fun execute(runnable: () -> Unit) {
            handler.post(runnable)
        }
    }
}

fun Scheduler.Companion.forExecutor(executor: Executor): Scheduler {
    return object : Scheduler {
        override fun execute(runnable: () -> Unit) {
            executor.execute(runnable)
        }
    }
}

fun <T> Publisher<T>.observeOn(scheduler: Scheduler): Publisher<T> {
    return Publisher { subscriber ->
        this@observeOn.subscribe(object : Subscriber<T> {
            override fun onComplete() {
                scheduler.execute {
                    subscriber.onComplete()
                }
            }

            override fun onError(error: Throwable) {
                scheduler.execute {
                    subscriber.onError(error)
                }
            }

            override fun onNext(item: T) {
                scheduler.execute {
                    subscriber.onNext(item)
                }
            }

            override fun onSubscribe(subscription: Subscription) {
                subscriber.onSubscribe(subscription)
            }
        })
    }
}