package io.rover.rover.services.concurrency

import android.os.Handler
import android.os.Looper
import io.rover.rover.core.logging.log
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


/**
 * Use this scheduler to run an operation on the main thread, receiving a Single<T> that yields the
 * returned value when the operation is complete.
 *
 * This should be a singleton.
 */
class MainThreadScheduler: Scheduler {
    // TODO: this should be injected maybe.
    private val looper = Looper.getMainLooper()

    private val handler = Handler(looper)

    private fun <T> schedule(runnable: () -> T): Single<T> {
        return object : Subscribable<T>() {
            override fun subscribe(subscriber: Subscriber<T>, scheduler: Scheduler): Subscription<T> {
                val subscription = super.subscribe(subscriber, scheduler)

                handler.post {
                    try {
                        // synchronously run the workload, now that we're in the main thread!
                        val result = runnable()
                        // and notify that we're done by emitting the result from the Subscribable.
                        onCompleted(result)
                    } catch (e: Throwable) {
                        onError(e)
                    }
                }

                return subscription
            }
        }
    }

    override fun <T> scheduleOperation(operation: () -> T): Single<T> = schedule(operation)

    override fun scheduleSideEffectOperation(operation: () -> Unit) {
        handler.post {
            operation()
        }
    }
}

/**
 * Use this scheduler to run an operation on a executor configured to run operations in the
 * background, receiving a Single<T> that yields the returned value when the operation is complete.
 *
 * This should be a singleton.
 */
class BackgroundExecutorServiceScheduler: Scheduler {
    // TODO: set parameters appropriately.
    private val executor = ThreadPoolExecutor(10, 20, 2, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>())

    private fun <T> schedule(runnable: () -> T): Single<T> {
        return object : Subscribable<T>() {
            override fun subscribe(subscriber: Subscriber<T>, scheduler: Scheduler): Subscription<T> {
                val subscription = super.subscribe(subscriber, scheduler)
                log.d("Subscribed!  Scheduling block into executor scheduler.")
                val output = FutureTask<Unit> {
                    try {
                        // synchronously run the workload, now that we're in the executor!
                        val result = runnable()
                        // and notify that we're done by emitting the result from the Subscribable.
                        onCompleted(result)
                    } catch (e: Throwable) {
                        onError(e)
                    }
                }
                executor.execute(output)
                return subscription
            }
        }
    }

    override fun <T> scheduleOperation(operation: () -> T): Single<T> = schedule(operation)

    override fun scheduleSideEffectOperation(operation: () -> Unit) {
        executor.execute(operation)
    }
}
