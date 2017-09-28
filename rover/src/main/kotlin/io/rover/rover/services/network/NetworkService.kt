package io.rover.rover.services.network

import android.os.Handler
import android.os.Looper
import io.rover.rover.core.domain.Context
import io.rover.rover.core.domain.Event
import io.rover.rover.core.domain.Experience
import io.rover.rover.core.domain.ID
import io.rover.rover.platform.DeviceIdentificationInterface
import io.rover.rover.services.network.requests.FetchExperienceRequest
import io.rover.rover.services.network.requests.SendEventsRequest
import java.io.IOException
import java.net.URL

class NetworkService(
    private val accountToken: String,
    private val endpoint: URL,
    private val client: NetworkClient,
    private val deviceIdentification: DeviceIdentificationInterface,
    private val wireEncoder: WireEncoderInterface,
    override var profileIdentifier: String?
): NetworkServiceInterface {

    private val mainThreadHandler = Handler(Looper.getMainLooper())

    private fun authHeaders(profileIdentifier: String?): HashMap<String, String> {
        val authHeaders = hashMapOf<String, String>()
        authHeaders["x-rover-account-token"] = accountToken

        authHeaders["x-rover-device-identifier"] = deviceIdentification.installationIdentifier

        val possibleProfileIdentifier = profileIdentifier ?: this.profileIdentifier
        if(possibleProfileIdentifier != null) {
            authHeaders["x-rover-profile-identifier"] = possibleProfileIdentifier
        }

        return authHeaders
    }

    private fun urlRequest(authHeaders: HashMap<String, String>): HttpRequest = HttpRequest(
        endpoint,
        hashMapOf<String, String>().apply {
            this["Content-Type"] = "application/json"

            authHeaders.entries.forEach { (key, value) ->
                this[key] = value
            }
        }
    )

    private fun <TEntity> httpResult(httpRequest: NetworkRequest<TEntity>, httpResponse: HttpClientResponse): NetworkResult<TEntity> =
        when (httpResponse) {
            is HttpClientResponse.ConnectionFailure -> NetworkResult.Error(httpResponse.reason, true)
            is HttpClientResponse.ApplicationError -> {
                NetworkResult.Error(
                    NetworkError.InvalidStatusCode(httpResponse.responseCode),
                    when {
                        // actually won't see any 200 codes here; already filtered about in the
                        // HttpClient response mapping.
                        httpResponse.responseCode < 300 -> false
                        // 3xx redirects
                        httpResponse.responseCode < 400 -> false
                        // 4xx request errors (we don't want to retry these; onus is likely on
                        // request creator).
                        httpResponse.responseCode < 500 -> false
                        // 5xx - any transient errors from the backend.
                        else -> true
                    }
                )
            }
            is HttpClientResponse.Success -> {
                try {
                    val body = httpResponse.bufferedInputStream.reader(Charsets.UTF_8).readText()
                    when(body) {
                        "" -> NetworkResult.Error<TEntity>(NetworkError.EmptyResponseData(), false)
                        else -> {
                            try {
                                NetworkResult.Success(
                                    httpRequest.decode(body, wireEncoder)
                                )
                            } catch (e: APIException) {
                                NetworkResult.Error<TEntity>(
                                    NetworkError.InvalidResponseData(e.message ?: "API returned unknown error."),
                                    // retry is not appropriate when we're getting a domain-level
                                    // error from the GraphQL API.
                                    false
                                )
                            }
                        }
                    }
                } catch (exception: IOException) {
                    NetworkResult.Error<TEntity>(exception, true)
                }
            }
        }

//    struct ResponseWrapper<T>: Decodable where T: Decodable {
//        let data: T
//    }

    fun <TEntity> uploadTask(request: NetworkRequest<TEntity>, completionHandler: ((NetworkResult<TEntity>) -> Unit)?): NetworkTask =
        uploadTask(request, profileIdentifier, completionHandler)

    /**
     * Make a request of the Rover cloud API.  Results are delivered into the provided
     * [completionHandler] callback, on the main thread.
     */
    fun <TEntity> uploadTask(request: NetworkRequest<TEntity>, profileIdentifier: String?, completionHandler: ((NetworkResult<TEntity>) -> Unit)?): NetworkTask {
        val authHeaders = authHeaders(profileIdentifier)
        val urlRequest = urlRequest(authHeaders)
        val bodyData = request.encode()

        return client.networkTask(urlRequest, bodyData) { httpClientResponse ->
            val result = httpResult(request, httpClientResponse)

            when (result) {
                is NetworkResult.Error -> {
                }
                is NetworkResult.Success -> completionHandler?.invoke(result)
            }

            completionHandler?.invoke(result)
        }
    }

    override fun fetchExperienceTask(experienceID: ID, completionHandler: ((NetworkResult<Experience>) -> Unit)?): NetworkTask {
        val request = FetchExperienceRequest(experienceID)
        return uploadTask(request) { experienceResult ->
            mainThreadHandler.run {
                completionHandler?.invoke(experienceResult)
            }
        }
    }

    override fun sendEventsTask(
        events: List<Event>,
        context: Context,
        profileIdentifier: String?,
        completionHandler: ((NetworkResult<String>) -> Unit)?
    ): NetworkTask {
        val request = SendEventsRequest(
            events,
            context,
            wireEncoder
        )
        return uploadTask(request) { uploadResult ->
            mainThreadHandler.run {
                completionHandler?.invoke(uploadResult)
            }
        }
    }
}

