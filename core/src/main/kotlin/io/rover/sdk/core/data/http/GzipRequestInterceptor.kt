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

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.GzipSink
import okio.IOException
import okio.buffer

/**
 * An OkHttp request interceptor that enables Gzip compression for request bodies.
 *
 * (Not needed for response bodies; OkHttp handles that automatically.)
 *
 * Courtesy of felipecsl in https://github.com/square/okhttp/issues/350.
 */
internal class GzipRequestInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (originalRequest.body == null) {
            val newRequest = originalRequest.newBuilder()
                .header("Content-Encoding", "gzip")
                .build()
            return chain.proceed(newRequest)
        }

        if (originalRequest.header("Content-Encoding") != null) {
            return chain.proceed(originalRequest)
        }

        val compressedRequest = originalRequest.newBuilder()
            .header("Content-Encoding", "gzip")
            .method(originalRequest.method, forceContentLength(gzip(originalRequest.body!!)))
            .build()

        return chain.proceed(compressedRequest)
    }

    /**
     * https://github.com/square/okhttp/issues/350
     */
    @Throws(IOException::class)
    private fun forceContentLength(requestBody: RequestBody): RequestBody {
        val buffer = Buffer()
        requestBody.writeTo(buffer)
        return object : RequestBody() {
            override fun contentType(): MediaType? {
                return requestBody.contentType()
            }

            override fun contentLength(): Long {
                return buffer.size
            }

            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                sink.write(buffer.snapshot())
            }
        }
    }

    private fun gzip(body: RequestBody): RequestBody {
        return object : RequestBody() {
            override fun contentType(): MediaType? {
                return body.contentType()
            }

            override fun contentLength(): Long {
                return -1 // We don't know the compressed length in advance!
            }

            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                val gzipSink = GzipSink(sink).buffer()
                body.writeTo(gzipSink)
                gzipSink.close()
            }
        }
    }

    companion object {
        private const val TAG = "GzipRequestInterceptor"
    }
}
