package io.rover.campaigns.core.platform

import android.annotation.SuppressLint
import android.os.Build
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * A builder that will produce an [Executor] suitable for multiplexing across many blocking I/O
 * operations.
 *
 * TODO make internal again after DI fixes.
 */
class IoMultiplexingExecutor {
    companion object {
        /**
         * This will produce an [Executor] tuned for multiplexing I/O, not for computation.
         *
         * Try to avoid doing computation on it.
         */
        @SuppressLint("NewApi")
        @JvmStatic
        fun build(executorName: String): Executor {
            val alwaysUseLegacyThreadPool = false

            val cpuCount = Runtime.getRuntime().availableProcessors()

            val useModernThreadPool = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !alwaysUseLegacyThreadPool

            return if (useModernThreadPool) {
                // The below is equivalent to:
                // Executors.newWorkStealingPool(availableProcessors * 100)

                // It's specifically meant for use in a ForkJoinTask work-stealing workload, but as a
                // side-effect it also configures an Executor that does a fair job of enforcing a
                // maximum thread pool size, which is difficult to do with the stock Executors due to an
                // odd design decision by the Java team a few decades ago:
                // https://github.com/kimchy/kimchy.github.com/blob/master/_posts/2008-11-23-juc-executorservice-gotcha.textile
                ForkJoinPool(
                    cpuCount * 100,
                    ForkJoinPool.ForkJoinWorkerThreadFactory { pool ->
                        ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool).apply {
                            // include our executor name in the worker thread names.
                            this.name = "Rover/IoMultiplexingExecutor($executorName)-${this.name}"
                        }
                    },
                    null, true)
            } else {
                // we'll use an unbounded thread pool on Android versions older than 21.

                // while one would expect that you could set a maxPoolSize value and have sane
                // scale-up-and-down behaviour, that is not how it works out.  ThreadPoolExecutor
                // actually will only create new threads (up to the max), when the queue you give it
                // reports itself as being full by means of the [Queue.offer] interface.  A common
                // workaround is to use SynchronousQueue, which always reports itself as full.
                // Unfortunately, this workaround prohibits the thread pool maximum size being enforced.
                // However, on casual testing for our use case it appears that for our I/O multiplexing
                // thread pool we can largely get away with this.
                ThreadPoolExecutor(
                    10,
                    Int.MAX_VALUE,
                    2,
                    TimeUnit.SECONDS,
                    SynchronousQueue<Runnable>()
                )
            }
        }
    }
}