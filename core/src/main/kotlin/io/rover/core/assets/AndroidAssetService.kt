package io.rover.core.assets

import android.graphics.Bitmap
import io.rover.core.data.NetworkResult
import io.rover.core.streams.PublishSubject
import io.rover.core.streams.Publishers
import io.rover.core.streams.Scheduler
import io.rover.core.streams.doOnNext
import io.rover.core.streams.doOnSubscribe
import io.rover.core.streams.filter
import io.rover.core.streams.flatMap
import io.rover.core.streams.map
import io.rover.core.streams.observeOn
import io.rover.core.streams.onErrorReturn
import io.rover.core.streams.subscribe
import io.rover.core.streams.subscribeOn
import io.rover.core.streams.timeout
import org.reactivestreams.Publisher
import java.net.URL
import java.util.concurrent.TimeUnit

internal class AndroidAssetService(
    imageDownloader: ImageDownloader,
    private val ioScheduler: Scheduler,
    private val mainThreadScheduler: Scheduler
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

    override fun getImageByUrl(url: URL): Publisher<NetworkResult<Bitmap>> {
        return Publishers.defer {
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
        }.subscribeOn(ioScheduler).map { pipelineResult ->
            when (pipelineResult) {
                is PipelineStageResult.Successful -> {
                    NetworkResult.Success(pipelineResult.output)
                }
                is PipelineStageResult.Failed -> {
                    NetworkResult.Error<Bitmap>(pipelineResult.reason, false)
                }
            }
        }.onErrorReturn { error ->
            NetworkResult.Error<Bitmap>(error, false) as NetworkResult<Bitmap>
        }.observeOn(
            mainThreadScheduler
        )
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
                    .onErrorReturn {
                        NetworkResult.Error<Bitmap>(it, false) as NetworkResult<Bitmap>
                    }
                    .map { Pair(url, it) }
            }
            .subscribe { (url, result) ->
                synchronized(outstanding) { outstanding.remove(url) }
                when (result) {
                    is NetworkResult.Success -> receivedImages.onNext(
                        ImageReadyEvent(url, result.response)
                    )
                }
            }
    }
}
