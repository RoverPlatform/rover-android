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

package io.rover.sdk.experiences.rich.compose.data

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.request.SuccessResult

class LargeImageInterceptor(
        val maxWidth: Int,
        val maxHeight: Int
) : Interceptor {
    private var maxPixels = maxWidth * maxHeight

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        return when (val result = chain.proceed(chain.request)) {
            is ErrorResult -> result
            is SuccessResult -> {
                val sumPixels = result.drawable.intrinsicWidth * result.drawable.intrinsicHeight

                if (sumPixels > maxPixels) {
                    return ErrorResult(
                            chain.request.error,
                            chain.request,
                            RuntimeException("Image is likely too large to draw within memory constraints: ${result.drawable.intrinsicWidth} x ${result.drawable.intrinsicHeight} > $maxWidth x $maxHeight")
                    )
                } else {
                    result
                }
            }
        }
    }
}