package io.rover.app.debug

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import io.rover.experiences.ui.containers.RoverActivity

class DebugMainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_debug_main)

         startActivity(
             RoverActivity.makeIntent(this, experienceId = "59e8b9d0d4459d00102c2958", campaignId = null)
         )
    }
}
