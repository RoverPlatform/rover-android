package io.rover.sample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import io.rover.rover.core.logging.log
import io.rover.rover.services.concurrency.BackgroundExecutorServiceScheduler
import io.rover.rover.services.concurrency.MainThreadScheduler
import io.rover.rover.services.concurrency.Single
import io.rover.rover.services.network.GraphQLNetworkService
import io.rover.rover.services.network.HttpClient
import io.rover.rover.services.network.HttpClientResponse
import io.rover.rover.services.network.NetworkServiceContract
import io.rover.rover.services.network.PlatformSimpleHttpClient
import java.net.URL

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

        val networkService = GraphQLNetworkService(
            "https://api.staging.rover.io",
            httpClient,
            scheduler
        )

        testButton.setOnClickListener {
            networkService.fetchExperience("donut").subscribe(
                completed = {
                    log.e("Experience fetched successfully!")
                },
                error = { error ->
                    log.e("thread is ${Thread.currentThread().id}, error: $error")
                    throw(error)
                },
                scheduler =  mainThreadScheduler
            )
        }
    }
}
