package io.rover.sample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import io.rover.rover.core.domain.ID
import io.rover.rover.core.logging.log
import io.rover.rover.platform.DeviceIdentification
import io.rover.rover.platform.LocalStorage
import io.rover.rover.platform.SharedPreferencesLocalStorage
import io.rover.rover.services.network.AsyncTaskAndHttpUrlConnectionNetworkClient
import io.rover.rover.services.network.NetworkResult
import io.rover.rover.services.network.NetworkService
import java.net.URL

class RoverSampleActivity : AppCompatActivity() {

    private val testButton by lazy {
        this.findViewById(R.id.test_button) as Button
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rover_sample)
        val networkClient = AsyncTaskAndHttpUrlConnectionNetworkClient()

        val networkService = NetworkService(
            "lol auth token",
            URL("https://api.staging.rover.io/graphql"),
            networkClient,
            DeviceIdentification(
                SharedPreferencesLocalStorage(applicationContext)
            ),
            null
        )

        testButton.setOnClickListener {
            networkService.fetchExperienceTask(ID("donut")) { result ->
                // we need a story about handling Android lifecycle
                when(result) {
                    is NetworkResult.Success -> {
                        log.e("Experience fetched successfully!")
                    }
                    is NetworkResult.Error -> {
                        log.e("Request failed: ${result.throwable.message}")
                    }
                }
            }.resume()
        }
    }
}
