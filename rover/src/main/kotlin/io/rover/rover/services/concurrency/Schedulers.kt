package io.rover.rover.services.concurrency

import android.os.Handler
import android.os.Looper
import java.util.concurrent.*


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
        return object : Subject<T>() {
            override fun subscribe(completed: (T) -> Unit, error: (Throwable) -> Unit, scheduler: Scheduler) {
                super.subscribe(completed, error, scheduler)

                handler.post {
                    try {
                        // synchronously run the workload, now that we're in the main thread!
                        val result = runnable()
                        // and notify that we're done by emitting the result from the Subject.
                        completed(result)
                    } catch (e: Throwable) {
                        error(e)
                    }
                }
            }
        }
    }

    override fun <T> scheduleOperation(operation: () -> T): Single<T> = schedule(operation)
}

/**
 * Use this scheduler to run an operation on a executor configured to run operations in the
 * background, receiving a Single<T> that yields the returned value when the operation is complete.
 *
 * This should be a singleton.
 */
class BackgroundExecutorServiceScheduler: Scheduler {
    // TODO: set parameters appropriately.
    private val executor = ThreadPoolExecutor(1, 2, 2000, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>())

    private fun <T> schedule(runnable: () -> T): Single<T> {

        return object : Subject<T>() {
            override fun subscribe(completed: (T) -> Unit, error: (Throwable) -> Unit, scheduler: Scheduler) {
                super.subscribe(completed, error, scheduler)
                val output = FutureTask<Unit> {
                    try {
                        // synchronously run the workload, now that we're in the executor!
                        val result = runnable()
                        // and notify that we're done by emitting the result from the Subject.
                        completed(result)
                    } catch (e: Throwable) {
                        error(e)
                    }
                }
                executor.execute(output)
            }
        }
    }

    override fun <T> scheduleOperation(operation: () -> T): Single<T> = schedule(operation)
}
