package io.rover.rover.services.assets

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import io.rover.rover.services.network.NetworkClient
import io.rover.rover.services.network.NetworkResult
import io.rover.rover.services.network.NetworkTask
import java.net.URL
import java.util.concurrent.Executor

class AndroidAssetService(
    networkClient: NetworkClient,
    private val ioExecutor: Executor
) : AssetService {
    private val mainThreadHandler = Handler(Looper.getMainLooper())

    private val synchronousImagePipeline = BitmapWarmGpuCacheStage(
        InMemoryBitmapCacheStage(
            DecodeToBitmapStage(
                AssetRetrievalStage(
                    networkClient
                )
            )
        )
    )

    override fun getImageByUrl(
        url: URL,
        completionHandler: ((NetworkResult<Bitmap>) -> Unit)
    ): NetworkTask {
        return SynchronousOperationNetworkTask(
            ioExecutor,
            { synchronousImagePipeline.request(url) },
            {
                mainThreadHandler.post {
                    completionHandler(
                        NetworkResult.Success(it)
                    )
                }
            }
        )
    }

    /**
     * A encapsulate a synchronous operation to an executor, yielding its result to the given
     * callback ([emitResult])
     */
    class SynchronousOperationNetworkTask<T>(
        private val executor: Executor,
        private val doSynchronousWorkload: () -> T,
        private val emitResult: (T) -> Unit
    ) : NetworkTask {
        private var cancelled = false

        override fun cancel() {
            cancelled = true
        }

        override fun resume() {
            executor.execute {
                val result = doSynchronousWorkload()

                if (!cancelled) {
                    emitResult(result)
                }
            }
        }
    }
}
