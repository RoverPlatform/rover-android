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

package io.rover.sdk.core.platform

import android.annotation.SuppressLint
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool

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

            // The below is equivalent to:
            // Executors.newWorkStealingPool(availableProcessors * 100)

            // It's specifically meant for use in a ForkJoinTask work-stealing workload, but as a
            // side-effect it also configures an Executor that does a fair job of enforcing a
            // maximum thread pool size, which is difficult to do with the stock Executors due to an
            // odd design decision by the Java team a few decades ago:
            // https://github.com/kimchy/kimchy.github.com/blob/master/_posts/2008-11-23-juc-executorservice-gotcha.textile
            return ForkJoinPool(
                cpuCount * 100,
                ForkJoinPool.ForkJoinWorkerThreadFactory { pool ->
                    ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool).apply {
                        // include our executor name in the worker thread names.
                        this.name = "Rover/IoMultiplexingExecutor($executorName)-${this.name}"
                    }
                },
                null,
                true
            )
        }
    }
}
