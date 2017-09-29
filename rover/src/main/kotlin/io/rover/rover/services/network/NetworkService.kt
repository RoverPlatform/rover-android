package io.rover.rover.services.network

import android.os.Handler
import android.os.Looper
import io.rover.rover.core.domain.Experience
import io.rover.rover.core.domain.ID
import io.rover.rover.platform.DeviceIdentificationInterface
import io.rover.rover.services.network.requests.FetchExperienceRequest
import java.io.IOException
import java.net.URL

class NetworkService(
    private val accountToken: String,
    private val endpoint: URL,
    private val client: NetworkClient,
    private val deviceIdentification: DeviceIdentificationInterface,
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

    private fun <T> httpResult(httpResponse: HttpClientResponse): NetworkResult<T> =
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
                val body = try {
                    httpResponse.bufferedInputStream.reader(Charsets.UTF_8).readText()
                } catch (exception: IOException) {
                    NetworkResult.Error<T>(exception, true)
                }


                when (body) {
                    null, "" -> NetworkResult.Error(NetworkError.EmptyResponseData(), false)
                    else -> {
                        // TODO we have our payload.  Now we have to do JSON decoding.

                        // NetworkResult.Success(...)
                        throw NotImplementedError()
                    }
                }
            }
        }


    /**
     * Encodes a payload into a JSON body.
     */
    private fun <T> bodyData(payload: T): String? = "{\"poop\": true}"

//    struct ResponseWrapper<T>: Decodable where T: Decodable {
//        let data: T
//    }

    fun <TRequest : NetworkRequest, T> uploadTask(request: TRequest, completionHandler: ((NetworkResult<T>) -> Unit)?): NetworkTask =
        uploadTask(request, profileIdentifier, completionHandler)

    fun <TRequest : NetworkRequest, T> uploadTask(request: TRequest, profileIdentifier: String?, completionHandler: ((NetworkResult<T>) -> Unit)?): NetworkTask {
        val authHeaders = authHeaders(profileIdentifier)
        val urlRequest = urlRequest(authHeaders)
        val bodyData = bodyData(request)

        return client.networkTask(urlRequest, bodyData) { httpClientResponse ->
            val result = httpResult<T>(httpClientResponse)

            when (result) {
                is NetworkResult.Error -> {
                }
                is NetworkResult.Success -> completionHandler?.invoke(result)
            }

            completionHandler?.invoke(result)
        }
    }

    override fun fetchExperienceTask(experienceID: ID, completionHandler: ((NetworkResult<Experience>) -> Unit)?): NetworkTask {
        val request = FetchExperienceRequest()
        return uploadTask<FetchExperienceRequest, Experience>(request) { experienceResult ->
            mainThreadHandler.run {
                completionHandler?.invoke(experienceResult)
            }
        }
    }
}

