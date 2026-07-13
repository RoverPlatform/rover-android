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

package io.rover.sdk.core.data.sync

import io.rover.sdk.core.data.http.HttpClientResponse
import io.rover.sdk.core.streams.Publishers
import io.rover.sdk.core.streams.Scheduler
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncCoordinatorTest {
    @Test
    fun graphQLAndStandaloneParticipantsStartConcurrently() {
        runBlocking {
            val executor = Executors.newCachedThreadPool()
            try {
                val ioScheduler = object : Scheduler {
                    override fun execute(runnable: () -> Unit) = executor.execute(runnable)
                }
                val mainThreadScheduler = object : Scheduler {
                    override fun execute(runnable: () -> Unit) = runnable()
                }

                val standaloneStarted = CountDownLatch(2)
                val graphQLObservedStandaloneStart = AtomicBoolean(false)
                val standaloneRuns = AtomicInteger(0)

                val syncClient = object : SyncClientInterface {
                    override fun executeSyncRequests(requests: List<SyncRequest>): org.reactivestreams.Publisher<HttpClientResponse> {
                        graphQLObservedStandaloneStart.set(standaloneStarted.await(1, TimeUnit.SECONDS))
                        return PublisherFactory.successPublisher(
                            onRequest = {}
                        )
                    }
                }

                val coordinator = SyncCoordinator(
                    ioScheduler = ioScheduler,
                    mainThreadScheduler = mainThreadScheduler,
                    syncClient = syncClient,
                )
                val participant = object : SyncParticipant {
                    override fun initialRequest(): SyncRequest {
                        return SyncRequest(
                            query = SyncQuery("test", "", emptyList(), emptyList()),
                            variables = emptyMap(),
                        )
                    }

                    override fun saveResponse(json: org.json.JSONObject): SyncResult {
                        return SyncResult.NoData
                    }
                }
                repeat(2) {
                    coordinator.registerStandaloneParticipant(object : SyncStandaloneParticipant {
                        override suspend fun sync(): Boolean {
                            standaloneRuns.incrementAndGet()
                            standaloneStarted.countDown()
                            delay(50)
                            return true
                        }
                    })
                }

                var syncResult: SyncCoordinatorInterface.Result? = null
                coordinator.performCombinedSync(
                    participants = listOf(participant),
                    requests = listOf(participant.initialRequest()!!),
                ).also { syncResult = it }

                assertEquals(SyncCoordinatorInterface.Result.Succeeded, syncResult)
                assertEquals(2, standaloneRuns.get())
                assertTrue("Expected GraphQL sync to observe standalone start before proceeding", graphQLObservedStandaloneStart.get())
            }
            finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun standaloneFailureCausesRetryNeededEvenWhenGraphQLSucceeds() {
        val executor = Executors.newCachedThreadPool()
        val ioScheduler = object : Scheduler {
            override fun execute(runnable: () -> Unit) = executor.execute(runnable)
        }
        val mainThreadScheduler = object : Scheduler {
            override fun execute(runnable: () -> Unit) = runnable()
        }

        val coordinator = SyncCoordinator(
            ioScheduler = ioScheduler,
            mainThreadScheduler = mainThreadScheduler,
            syncClient = object : SyncClientInterface {
                override fun executeSyncRequests(requests: List<SyncRequest>) = PublisherFactory.successPublisher()
            },
        )
        val participant = object : SyncParticipant {
            override fun initialRequest(): SyncRequest {
                return SyncRequest(
                    query = SyncQuery("test", "", emptyList(), emptyList()),
                    variables = emptyMap(),
                )
            }

            override fun saveResponse(json: org.json.JSONObject): SyncResult {
                return SyncResult.NoData
            }
        }
        coordinator.registerStandaloneParticipant(object : SyncStandaloneParticipant {
            override suspend fun sync(): Boolean = false
        })

        val result = runBlocking {
            coordinator.performCombinedSync(
                participants = listOf(participant),
                requests = listOf(participant.initialRequest()!!),
            )
        }
        assertEquals(SyncCoordinatorInterface.Result.RetryNeeded, result)
        executor.shutdownNow()
    }

    @Test
    fun resetCancelsStandaloneSiblingsBeforeRunningResetHandler() {
        val executor = Executors.newCachedThreadPool()
        val ioScheduler = object : Scheduler {
            override fun execute(runnable: () -> Unit) = executor.execute(runnable)
        }
        val mainThreadScheduler = object : Scheduler {
            override fun execute(runnable: () -> Unit) = runnable()
        }

        val siblingCancelledAndFinished = AtomicBoolean(false)
        val resetObservedSiblingFinished = AtomicBoolean(false)

        val resetHandler = object : SyncResetHandler {
            override suspend fun resetAfterSyncCancellation() {
                resetObservedSiblingFinished.set(siblingCancelledAndFinished.get())
            }
        }

        val coordinator = SyncCoordinator(
            ioScheduler = ioScheduler,
            mainThreadScheduler = mainThreadScheduler,
            syncClient = object : SyncClientInterface {
                override fun executeSyncRequests(requests: List<SyncRequest>) = PublisherFactory.successPublisher()
            },
        )

        coordinator.registerStandaloneParticipant(object : SyncStandaloneParticipant {
            override suspend fun sync(): Boolean {
                try {
                    delay(5_000)
                    return true
                } finally {
                    siblingCancelledAndFinished.set(true)
                }
            }
        })
        coordinator.registerStandaloneParticipant(object : SyncStandaloneParticipant {
            override suspend fun sync(): Boolean {
                delay(50)
                throw SyncResetRequiredException(resetHandler)
            }
        })
        val participant = object : SyncParticipant {
            override fun initialRequest(): SyncRequest {
                return SyncRequest(
                    query = SyncQuery("test", "", emptyList(), emptyList()),
                    variables = emptyMap(),
                )
            }

            override fun saveResponse(json: org.json.JSONObject): SyncResult {
                return SyncResult.NoData
            }
        }

        val result = runBlocking {
            coordinator.performCombinedSync(
                participants = listOf(participant),
                requests = listOf(participant.initialRequest()!!),
            )
        }

        assertEquals(SyncCoordinatorInterface.Result.RetryNeeded, result)
        assertTrue("Expected reset handler to run after sibling cancellation cleanup", resetObservedSiblingFinished.get())
        executor.shutdownNow()
    }

    private object PublisherFactory {
        fun successPublisher(
            onRequest: () -> Unit = {},
        ) = Publishers.create<HttpClientResponse> { subscriber ->
            onRequest()
            subscriber.onNext(
                HttpClientResponse.Success(
                    BufferedInputStream(ByteArrayInputStream("""{"data":{}}""".toByteArray()))
                )
            )
            subscriber.onComplete()
        }
    }
}
