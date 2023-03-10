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

@file:JvmName("Schedulers")

package io.rover.sdk.core.streams

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
