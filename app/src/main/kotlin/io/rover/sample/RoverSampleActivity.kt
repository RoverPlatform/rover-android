package io.rover.sample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.facebook.stetho.urlconnection.ByteArrayRequestEntity
import com.facebook.stetho.urlconnection.StethoURLConnectionManager
import io.rover.rover.core.logging.log

import io.rover.rover.platform.DateFormatting
import io.rover.rover.platform.DeviceIdentification
import io.rover.rover.platform.SharedPreferencesLocalStorage
import io.rover.rover.services.network.AsyncTaskAndHttpUrlConnectionInterception
import io.rover.rover.services.network.AsyncTaskAndHttpUrlConnectionInterceptor
import io.rover.rover.services.network.AsyncTaskAndHttpUrlConnectionNetworkClient
import io.rover.rover.services.network.NetworkResult
import io.rover.rover.services.network.NetworkService
import io.rover.rover.services.network.NetworkServiceInterface
import io.rover.rover.services.network.WireEncoder
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class RoverSampleActivity : AppCompatActivity() {

    private val testButton by lazy {
        this.findViewById(R.id.test_button) as Button
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rover_sample)
        val networkClient = AsyncTaskAndHttpUrlConnectionNetworkClient()

        networkClient.registerInterceptor(
            StethoRoverInterceptor()
        )

        val networkService = NetworkService(
            "API key goes here",
            URL("https://api.rover.io/graphql"),
            networkClient,
            DeviceIdentification(
                SharedPreferencesLocalStorage(applicationContext)
            ),
            WireEncoder(DateFormatting()),
            null
        ) as NetworkServiceInterface

        testButton.setOnClickListener {
//            networkService.fetchExperienceTask(ID("59c1893c46495d0011899445")) { result ->
//                // we need a story about handling Android lifecycle
//                when(result) {
//                    is NetworkResult.Success -> {
//                        log.e("Experience fetched successfully!")
//                    }
//                    is NetworkResult.Error -> {
//                        log.e("Request failed: ${result.throwable.message}")
//                    }
//                }
//            }.resume()

//            networkService.sendEventsTask(
//                listOf(
//                    Event(
//                        hashMapOf(
//                            Pair("a key", "hi.")
//                        ),
//                        "I am event",
//                        Date(),
//                        UUID.randomUUID()
//                    )
//                ),
//                Context(
//                    null, null, null, null, null, null, null, null, null, null, null, null, null,
//                    null, null, null, null, null, null, null, null, null, null, null
//                ), null
//            ) { result ->
//                when(result) {
//                    is NetworkResult.Success -> log.e("Sent successfully!")
//                    is NetworkResult.Error -> log.e("Failed: ${result.throwable.message}")
//                }
//            }.resume()

            networkService.fetchStateTask { result ->
                when(result) {
                    is NetworkResult.Success -> log.e("DeviceState fetched successfully: ${result.response}")
                    is NetworkResult.Error -> log.e("Failed to fetch device: ${result.throwable.message}")
                }
            }.resume()
        }
    }
}

/**
 * If you want to be able to see the requests made by the Rover SDK to our API in
 * [Stetho's](http://facebook.github.io/stetho/) network inspector, copy this class into your
 * application and set an instance of it on the [AsyncTaskAndHttpUrlConnectionNetworkClient] with
 * [AsyncTaskAndHttpUrlConnectionNetworkClient.registerInterceptor] (DI instructions for
 * users to follow).
 */
class StethoRoverInterceptor : AsyncTaskAndHttpUrlConnectionInterceptor {
    override fun onOpened(httpUrlConnection: HttpURLConnection, requestPath: String, body: ByteArray): AsyncTaskAndHttpUrlConnectionInterception {
        val connManager = StethoURLConnectionManager(requestPath)
        connManager.preConnect(httpUrlConnection, ByteArrayRequestEntity(body))

        return object : AsyncTaskAndHttpUrlConnectionInterception {
            override fun onConnected() {
                connManager.postConnect()
            }

            override fun onError(exception: IOException) {
                connManager.httpExchangeFailed(exception)
            }

            override fun sniffStream(source: InputStream): InputStream =
                connManager.interpretResponseStream(source)
        }
    }
}