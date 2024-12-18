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

package io.rover.sdk.core.data.http

import android.content.pm.PackageInfo
import io.rover.sdk.core.platform.setRoverUserAgent
import io.rover.sdk.core.streams.map
import io.rover.sdk.core.streams.onErrorReturn
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.reactivestreams.Publisher

class OkHttpNetworkClient(
    private val appPackageInfo: PackageInfo
) : NetworkClient {
    private var okHttpClient = OkHttpClient()

    private var gzipBodyEnabledOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(GzipRequestInterceptor())
        .build()

    override fun request(request: HttpRequest, bodyData: String?, gzip: Boolean): Publisher<HttpClientResponse> {

        val builder = Request.Builder()
        builder.url(request.url)
        request.headers.forEach {
            builder.addHeader(it.key, it.value)
        }

        val contentType = request.headers["Content-Type"]

        val requestBody = bodyData?.toRequestBody(contentType?.toMediaTypeOrNull() ?: "application/json".toMediaTypeOrNull())

        // verb
        when (request.verb) {
            HttpVerb.GET -> builder.get()
            HttpVerb.POST -> builder.post(requestBody ?: "".toRequestBody())
            HttpVerb.PUT -> builder.put(requestBody ?: "".toRequestBody())
            HttpVerb.DELETE -> builder.delete()
        }

        // user agent
        builder.setRoverUserAgent(appPackageInfo)

        val okHttpRequest = builder.build()
        val client = if (gzip) gzipBodyEnabledOkHttpClient else okHttpClient
        return client.newCallPublisher(okHttpRequest)
            .map { response ->
                when(response.code) {
                    in 200..299, 304 -> {
                        val stream = response.body?.byteStream()?.buffered()
                        HttpClientResponse.Success(stream!!)
                    }

                    else -> {
                        val errorBody = response.body?.string()
                        HttpClientResponse.ApplicationError(
                            response.code,
                            errorBody ?: "No reason given"
                        )
                    }
                }
            }
            .onErrorReturn { error ->
                HttpClientResponse.ConnectionFailure(error)
            }
    }
}

fun OkHttpClient.newCallPublisher(request: Request): Publisher<okhttp3.Response> {
    return Publisher { subscriber ->
        val call = this.newCall(request)
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                subscriber.onError(e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                subscriber.onNext(response)
                subscriber.onComplete()
            }
        })
    }
}


