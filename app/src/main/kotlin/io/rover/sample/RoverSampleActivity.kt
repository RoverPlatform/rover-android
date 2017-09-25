package io.rover.sample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import io.rover.rover.core.logging.log
import io.rover.rover.services.concurrency.BackgroundExecutorServiceScheduler
import io.rover.rover.services.concurrency.MainThreadScheduler
import io.rover.rover.services.network.NetworkService
import io.rover.rover.services.network.PlatformSimpleHttpClient

class RoverSampleActivity : AppCompatActivity() {

    private val testButton by lazy {
        this.findViewById(R.id.test_button) as Button
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rover_sample)
        val scheduler = BackgroundExecutorServiceScheduler()
        val mainThreadScheduler = MainThreadScheduler()
        val httpClient = PlatformSimpleHttpClient(
            scheduler
        )

        val networkService = NetworkService(
            "https://api.staging.rover.io",
            httpClient,
            scheduler
        )

        testButton.setOnClickListener {
            networkService.fetchExperience("donut").call {
                // we need a story about handling Android lifecycle
                log.e("Experience fetched successfully!")
            }
        }
    }
}
