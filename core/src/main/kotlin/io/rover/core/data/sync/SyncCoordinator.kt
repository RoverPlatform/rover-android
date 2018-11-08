package io.rover.core.data.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.rover.core.Rover
import io.rover.core.data.graphql.getObjectIterable
import io.rover.core.data.http.HttpClientResponse
import io.rover.core.logging.log
import io.rover.core.streams.PublishSubject
import io.rover.core.streams.Publishers
import io.rover.core.streams.Scheduler
import io.rover.core.streams.blockForResult
import io.rover.core.streams.doOnNext
import io.rover.core.streams.doOnSubscribe
import io.rover.core.streams.filter
import io.rover.core.streams.first
import io.rover.core.streams.flatMap
import io.rover.core.streams.observeOn
import io.rover.core.streams.shareHotAndReplay
import io.rover.core.streams.subscribeOn
import org.json.JSONException
import org.json.JSONObject
import org.reactivestreams.Publisher
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncCoordinator(
    private val context: Context,
    private val ioScheduler: Scheduler,
    private val mainThreadScheduler: Scheduler,
    private val syncClient: SyncClientInterface,
    private val hourlyTargetRefreshFrequency: Int = 1
): SyncCoordinatorInterface {

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
            hourlyTargetRefreshFrequency.toLong(), TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance().enqueueUniquePeriodicWork("rover-sync", ExistingPeriodicWorkPolicy.REPLACE, request)
    }

    private fun sync(participants: List<SyncParticipant>, requests: List<SyncRequest>): Publisher<SyncCoordinatorInterface.Result> {
        // this chains recursively.
        return if(!requests.isEmpty() && !participants.isEmpty()) {
            syncClient
                .executeSyncRequests(requests)
                .subscribeOn(ioScheduler)
                .flatMap { httpResponse ->
                    when(httpResponse) {
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
                            val nextParticipants =  participants.mapNotNull { syncParticipant ->
                                val syncResult = syncParticipant.saveResponse(json)
                                if(syncResult is SyncResult.NewData && syncResult.nextRequest != null) {
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

    enum class Action {
        AttemptSync
    }

    init {
        // trigger sync for the next loop of the Android main looper.  This will happen
        // after all of the Rover DI has completed and thus all of the participants have been registered.
        Handler(Looper.getMainLooper()).post {
            subject.onNext(Action.AttemptSync)
        }
    }

    class WorkManagerWorker(
        context: Context,
        params: WorkerParameters
    ): Worker(context, params) {
        override fun doWork(): Result {
            val result = Rover.shared?.resolve(SyncCoordinatorInterface::class.java)
                ?.sync()
                ?.first()
                ?.blockForResult(300)?.first()
            return when(result) {
                SyncCoordinatorInterface.Result.Succeeded -> Result.SUCCESS
                SyncCoordinatorInterface.Result.RetryNeeded -> Result.RETRY
                null -> {
                    log.w("Rover isn't initialized or CoreAssembler hasn't been added, but the background sync job with work manager is still scheduled. Marking as failed.")
                    Result.FAILURE
                }
            }
        }
    }
}
