package io.rover.app.debug

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import io.rover.experiences.ui.containers.ExperienceActivity

class DebugMainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_debug_main)

         startActivity(
             ExperienceActivity.makeIntent(this, experienceId = "INSERT ME", campaignId = null)
         )
    }
}
