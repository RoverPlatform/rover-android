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

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.rover.sdk.core.Rover
import io.rover.sdk.core.data.graphql.getObjectIterable
import io.rover.sdk.core.data.http.HttpClientResponse
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.streams.PublishSubject
import io.rover.sdk.core.streams.Publishers
import io.rover.sdk.core.streams.Scheduler
import io.rover.sdk.core.streams.blockForResult
import io.rover.sdk.core.streams.doOnNext
import io.rover.sdk.core.streams.doOnSubscribe
import io.rover.sdk.core.streams.filter
import io.rover.sdk.core.streams.first
import io.rover.sdk.core.streams.flatMap
import io.rover.sdk.core.streams.map
import io.rover.sdk.core.streams.observeOn
import io.rover.sdk.core.streams.share
import io.rover.sdk.core.streams.shareHotAndReplay
import io.rover.sdk.core.streams.subscribeOn
import org.json.JSONException
import org.json.JSONObject
import org.reactivestreams.Publisher
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncCoordinator(
    private val ioScheduler: Scheduler,
    mainThreadScheduler: Scheduler,
    private val syncClient: SyncClientInterface,
    private val hourlyTargetRefreshFrequency: Int = 1
) : SyncCoordinatorInterface {

    override fun registerParticipant(participant: SyncParticipant) {
        participants.add(participant)
    }

    override fun sync(): Publisher<SyncCoordinatorInterface.Result> {
        // this allows us to wait for the result of an already running sync instead of starting a
        // fresh one.
        return chain.doOnSubscribe {
            subject.onNext(Action.AttemptSync)
        }
    }

    override fun triggerSync() {
        subject.onNext(Action.AttemptSync)
    }

    override fun ensureBackgroundSyncScheduled() {
        log.v("Ensuring that Rover background sync is registered to execute every $hourlyTargetRefreshFrequency hours.")
        val request = PeriodicWorkRequestBuilder<WorkManagerWorker>(
            hourlyTargetRefreshFrequency.toLong(),
            TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance().enqueueUniquePeriodicWork(
            "rover-sync",
            // note: if the parameters are changed, then the existing periodic work needs to be
            // manually cancelled and the current work given a new unique work name.
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun sync(participants: List<SyncParticipant>, requests: List<SyncRequest>): Publisher<SyncCoordinatorInterface.Result> {
        // this chains recursively.
        return if (!requests.isEmpty() && !participants.isEmpty()) {
            syncClient
                .executeSyncRequests(requests)
                .subscribeOn(ioScheduler)
                .flatMap { httpResponse ->
                    when (httpResponse) {
                        is HttpClientResponse.ConnectionFailure -> {
                            log.w("Unable to sync due to connection failure: ${httpResponse.reason}")
                            Publishers.just(SyncCoordinatorInterface.Result.RetryNeeded)
                        }
                        is HttpClientResponse.ApplicationError -> {
                            log.w("Unable to sync due to HTTP ${httpResponse.responseCode}, '${httpResponse.reportedReason}'")
                            Publishers.just(SyncCoordinatorInterface.Result.RetryNeeded)
                        }
                        is HttpClientResponse.Success -> {
                            val body = try {
                                httpResponse.bufferedInputStream.use {
                                    it.reader(Charsets.UTF_8).readText()
                                }
                            } catch (exception: IOException) {
                                log.w("Sync failed due to network read error: $exception")
                                return@flatMap Publishers.just(SyncCoordinatorInterface.Result.RetryNeeded)
                            }

                            val json = try {
                                val parsed = JSONObject(body)
                                val possibleErrors = parsed.optJSONArray("errors")
                                if (possibleErrors != null) {
                                    log.w("Unable to sync due to errors reported by GraphQL: ${possibleErrors.getObjectIterable().map { it.toString() }}")
                                    return@flatMap Publishers.just(SyncCoordinatorInterface.Result.RetryNeeded)
                                } else {
                                    parsed
                                }
                            } catch (e: JSONException) {
                                log.w("Unable to sync due to receiving invalid JSON: $e")
                                return@flatMap Publishers.just(SyncCoordinatorInterface.Result.RetryNeeded)
                            }

                            // TODO: what if there is a Failed syncresult??
                            val nextParticipants = participants.mapNotNull { syncParticipant ->
                                val syncResult = syncParticipant.saveResponse(json)
                                if (syncResult is SyncResult.NewData && syncResult.nextRequest != null) {
                                    Pair(syncParticipant, syncResult.nextRequest)
                                } else null
                            }

                            log.v("Recursing to the ${nextParticipants.count()} next sync participants.")
                            sync(nextParticipants.map { it.first }, nextParticipants.map { it.second })
                        }
                    }
                }
        } else {
            log.v("No more sync work to do. Complete.")
            Publishers.just(SyncCoordinatorInterface.Result.Succeeded)
        }
    }

    private val participants: MutableSet<SyncParticipant> = mutableSetOf()

    private val subject = PublishSubject<Action>()

    @Volatile
    private var executing = false

    private val chain = subject
        .observeOn(ioScheduler)
        .filter {
            // filter out all incoming requests if a sync is already executing.
            synchronized(executing) { !executing }
        }
        .doOnNext {
            synchronized(executing) { executing = true }
        }.flatMap {
            log.v("Starting sync with ${participants.count()} participants.")
            sync(
                // starting with all the registered participants.
                this.participants.toList(),
                this.participants.mapNotNull { it.initialRequest() }
            ).doOnNext { log.v("Sync completed with: $it") }
        }.doOnNext {
            synchronized(executing) { executing = false }
        }.observeOn(mainThreadScheduler).shareHotAndReplay(0).subscribeOn(mainThreadScheduler)

    override val syncResults: Publisher<SyncCoordinatorInterface.Result> = chain

    override val updates: Publisher<Unit> = chain.filter { it == SyncCoordinatorInterface.Result.Succeeded }.map { Unit }.share()

    enum class Action {
        AttemptSync
    }

    class WorkManagerWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {
        override fun doWork(): Result {
            val result = try {
                Rover.failableShared?.resolve(SyncCoordinatorInterface::class.java)
                    ?.sync()
                    ?.first()
                    ?.blockForResult(300)?.first()
            } catch (e: Exception) {
                log.w("Unexpected failure running background sync: $e")
                return Result.retry()
            }
            return when (result) {
                SyncCoordinatorInterface.Result.Succeeded -> Result.success()
                SyncCoordinatorInterface.Result.RetryNeeded -> Result.retry()
                null -> {
                    log.w("Rover isn't initialized or CoreAssembler hasn't been added, but the background sync job with work manager is still scheduled. Marking as failed.")
                    Result.failure()
                }
            }
        }
    }
}
