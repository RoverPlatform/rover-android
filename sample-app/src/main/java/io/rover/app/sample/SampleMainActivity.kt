package io.rover.app.sample

import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import io.rover.sdk.ui.containers.RoverActivity

class SampleMainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_sample_main)

        // Example of manually starting an experience with an experienceId
        startActivity(
             RoverActivity.makeIntent(this, experienceId = "my-experience-id", campaignId = null)
         )


        val uri : Uri? = intent.data
        // Tries to retrieve experienceId from last path segment
        val possibleExperienceId = uri?.lastPathSegment

        // Tries to retrieve campaignId query parameter
        val possibleCampaignId = uri?.getQueryParameter("campaignID")

        // A simple routing example follows:
        // Your app can handle the intent data as it prefers - here, we're handling a simple deep
        // link scheme and a universal link domain as defined in the manifest.
        if (uri?.scheme == getString(R.string.uri_scheme) && uri?.host == "presentExperience" && possibleExperienceId != null) {
            startActivity(RoverActivity.makeIntent(packageContext = this, experienceId = possibleExperienceId, campaignId = possibleCampaignId))
        } else if(uri?.scheme in listOf("http", "https") && uri != null && uri.host == getString(R.string.associated_domain)) {
            startActivity(RoverActivity.makeIntent(packageContext = this, experienceUrl = uri))
        }
    }
}
