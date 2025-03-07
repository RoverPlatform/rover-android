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

package io.rover.sdk.experiences.rich.compose.ui.layers

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import io.rover.sdk.core.logging.log
import io.rover.sdk.experiences.data.URLRequest
import io.rover.sdk.experiences.data.authenticateRequest
import io.rover.sdk.experiences.rich.compose.model.nodes.DataSource
import io.rover.sdk.experiences.rich.compose.model.values.HttpMethod
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.data.Interpolator
import io.rover.sdk.experiences.rich.compose.ui.data.makeDataContext
import io.rover.sdk.experiences.rich.compose.ui.data.resolveJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun DataSourceLayer(node: DataSource, modifier: Modifier = Modifier) {
    val tag = "DataSourceLayer"
    val dataContext = makeDataContext(
        userInfo = Environment.LocalUserInfo.current?.invoke() ?: emptyMap(),
        urlParameters = Environment.LocalUrlParameters.current,
        deviceContext = Environment.LocalDeviceContext.current,
        data = Environment.LocalData.current
    )
    val interpolator = Interpolator(
        dataContext
    )

    val services = Environment.LocalServices.current ?: run {
        Log.e(tag, "Services not injected")
        return
    }

    val api by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.rover.io")
            .addConverterFactory(MoshiConverterFactory.create())
            .client(services.httpClient.client)
            .build()
            .create(DataSourceAPI::class.java)
    }

    val scope = rememberCoroutineScope()

    var state: State by remember { mutableStateOf(State.Loading) }

    // Interpolation on URL, headers, and body. If interpolation fails, yield empty.
    val url = interpolator.interpolate(node.url) ?: return
    val requestBody = node.httpBody?.let { interpolator.interpolate(it) ?: return@DataSourceLayer }
    var headers = node.headers.associateBy({
        it.key
    }, {
        interpolator.interpolate(it.value) ?: return@DataSourceLayer
    })

    val urlRequest = URLRequest(
        url = url,
        method = node.httpMethod,
        headers = HashMap(
            headers
        ),
        body = requestBody
    )

    val authorizerHandler = Environment.LocalAuthorizerHandler.current
    val authenticationContext = Environment.LocalAuthenticationContext.current

    LaunchedEffect(key1 = urlRequest) {
        state = State.Loading

        do {
            scope.launch {
                // make a copy of authorizedRequest, so the authorizer mutating it
                // won't re-trigger LaunchedEffect.
                // perform SDK authentication, then run the custom Authorizers
                val authorizedRequest = urlRequest.copy().let {
                    authenticationContext?.authenticateRequest(it) ?: it
                }
                authorizerHandler?.invoke(authorizedRequest)

                val response = try {
                    when (authorizedRequest.method) {
                        HttpMethod.GET -> {
                            api.get(authorizedRequest.url, authorizedRequest.headers)
                        }
                        HttpMethod.PUT -> {
                            val body = authorizedRequest.body
                            if (body == null) {
                                api.put(authorizedRequest.url, authorizedRequest.headers)
                            } else {
                                val contentType = "text/plain".toMediaType()
                                val requestBody = RequestBody.create(contentType, body)
                                api.putWithBody(authorizedRequest.url, authorizedRequest.headers, requestBody)
                            }
                        }
                        HttpMethod.POST -> {
                            val body = authorizedRequest.body
                            if (body == null) {
                                api.post(authorizedRequest.url, authorizedRequest.headers)
                            } else {
                                val contentType = "text/plain".toMediaType()
                                val requestBody = RequestBody.create(contentType, body)
                                api.postWithBody(authorizedRequest.url, authorizedRequest.headers, requestBody)
                            }
                        }
                    }
                } catch (e: Exception) {
                    log.w("Data source request failed, network error: $e")
                    state = State.Failed
                    return@launch
                }

                if (response.isSuccessful) {
                    val data = withContext(Dispatchers.IO) {
                        val byteResult = kotlin.runCatching {
                            response.body()?.string()
                        }

                        if (byteResult.isSuccess) {
                            return@withContext byteResult.getOrNull()?.let { resolveJson(it.toString()) }
                        } else {
                            log.w("Data source read error: ${byteResult.exceptionOrNull()}")
                        }
                    }
                    log.d("Data source loaded successfully.")
                    state = State.Success(data)
                } else {
                    log.w("Data source request failed: HTTP ${response.code()}: ${response.message()}")
                }
            }
            delay((node.pollInterval.seconds))
        } while (node.pollInterval > 0)
    }

    when (val currentState = state) {
        State.Loading -> {
            // TODO: replace this with a placeholder once performance issues are addressed.
            // CircularProgressIndicator(modifier = StripPackedIntrinsics())
        }
        is State.Failed -> {
            // Disappear; composable is just empty.
        }
        is State.Success -> {
            CompositionLocalProvider(Environment.LocalData provides currentState.data) {
                Children(children = node.children, modifier = modifier)
            }
        }
    }
}

private sealed class State {
    object Loading : State()
    data class Success(val data: Any?) : State()
    object Failed : State()
}

private interface DataSourceAPI {
    @GET
    suspend fun get(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Response<ResponseBody>

    @PUT
    suspend fun put(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Response<ResponseBody>

    @PUT
    suspend fun putWithBody(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: RequestBody
    ): Response<ResponseBody>

    @POST
    suspend fun post(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Response<ResponseBody>

    @POST
    suspend fun postWithBody(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: RequestBody
    ): Response<ResponseBody>
}
