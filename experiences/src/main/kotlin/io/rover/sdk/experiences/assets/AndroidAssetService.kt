package io.rover.sdk.experiences.assets

import android.graphics.Bitmap
import io.rover.sdk.experiences.logging.log
import io.rover.sdk.experiences.platform.debugExplanation
import io.rover.sdk.core.streams.PublishSubject
import io.rover.sdk.core.streams.Publishers
import io.rover.sdk.core.streams.Scheduler
import io.rover.sdk.core.streams.doOnNext
import io.rover.sdk.core.streams.doOnSubscribe
import io.rover.sdk.core.streams.filter
import io.rover.sdk.core.streams.flatMap
import io.rover.sdk.core.streams.map
import io.rover.sdk.core.streams.observeOn
import io.rover.sdk.core.streams.onErrorReturn
import io.rover.sdk.core.streams.retry
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.core.streams.subscribeOn
import io.rover.sdk.core.streams.timeout
import org.reactivestreams.Publisher
import java.net.URL
import java.util.concurrent.TimeUnit

internal open class AndroidAssetService(
        imageDownloader: ImageDownloader,
        private val ioScheduler: Scheduler,
        mainThreadScheduler: Scheduler
) : AssetService {
    private val synchronousImagePipeline = BitmapWarmGpuCacheStage(
        InMemoryBitmapCacheStage(
            DecodeToBitmapStage(
                AssetRetrievalStage(
                    imageDownloader
                )
            )
        )
    )

    override fun imageByUrl(
        url: URL
    ): Publisher<Bitmap> {
        return receivedImages
            .filter { it.url == url }
            .map { it.bitmap }
            .doOnSubscribe {
                // kick off an initial fetch if one is not already running.
                tryFetch(url)
            }
    }

    override fun tryFetch(url: URL) {
        requests.onNext(url)
    }

    override fun getImageByUrl(url: URL): Publisher<PipelineStageResult<Bitmap>> {
        return Publishers.defer<PipelineStageResult<Bitmap>> {
            Publishers.just(
                // this block will be dispatched onto the ioExecutor by
                // SynchronousOperationNetworkTask.

                // ioExecutor is really only intended for I/O multiplexing only: it spawns many more
                // threads than CPU cores.  However, I'm bending that rule a bit by having image
                // decoding occur inband.  Thankfully, the risk of that spamming too many CPU-bound
                // workloads across many threads is mitigated by the HTTP client library
                // (HttpURLConnection, itself internally backed by OkHttp inside the Android
                // standard library) limiting concurrent image downloads from the same origin, which
                // most of the images in Rover experiences will be.
                synchronousImagePipeline.request(url)
            )
        }.onErrorReturn { error ->
            PipelineStageResult.Failed<Bitmap>(error, false) as PipelineStageResult<Bitmap>
        }
    }

    private data class ImageReadyEvent(
        val url: URL,
        val bitmap: Bitmap
    )

    private val requests = PublishSubject<URL>()

    private val receivedImages = PublishSubject<ImageReadyEvent>()

    init {
        val outstanding: MutableSet<URL> = mutableSetOf()
        requests
            .filter { url ->
                synchronized(outstanding) { url !in outstanding }
            }
            .doOnNext { synchronized(outstanding) { outstanding.add(it) } }
            .flatMap { url ->
                getImageByUrl(url)
                    .timeout(10, TimeUnit.SECONDS)
                    // handle any unexpected failures in the image processing pipeline
                    .onErrorReturn {
                        PipelineStageResult.Failed<Bitmap>(it, false) as PipelineStageResult<Bitmap>
                    }
                    .map { result ->
                        if (result is PipelineStageResult.Failed<Bitmap> && result.retry) {
                            throw Exception("Do Retry.", result.reason)
                        }
                        result
                    }
                    .retry(3)
                    .map { Pair(url, it) }
                    .subscribeOn(ioScheduler)
            }
            .observeOn(mainThreadScheduler)
            .subscribe({ (url, result) ->
                synchronized(outstanding) { outstanding.remove(url) }
                when (result) {
                    is PipelineStageResult.Successful -> {
                        receivedImages.onNext(
                            ImageReadyEvent(url, result.output)
                        )
                    }
                    is PipelineStageResult.Failed -> {
                        log.w("Failed to fetch image from URL $url: ${result.reason.debugExplanation()}")
                    }
                }
            }, {
                log.w("image request failed ${it.debugExplanation()}")
            })
    }
}
