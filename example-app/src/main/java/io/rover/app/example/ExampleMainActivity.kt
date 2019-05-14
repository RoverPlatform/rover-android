package io.rover.app.example

import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import io.rover.sdk.ui.containers.RoverActivity

class ExampleMainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_example_main)

        // Example of manually starting an experience with an experienceId
        // startActivity(
        //     RoverActivity.makeIntent(this, experienceId = "my-experience-id", campaignId = null)
        // )

        val uri : Uri = intent.data ?: return

        // You will need to setup a specific URL structure to be used for presenting Rover
        // experiences in your app. The simplest approach is to use a specific URL path/host and
        // include the experience ID and (optional) campaign ID as query parameters. The manifest
        // included with this sample app and below example code demonstrates how to route URLs in
        // the format `example://experience?id=<EXPERIENCE_ID>&campaignID=<CAMPAIGN_ID>` to a Rover
        // experience.

        // Tries to retrieve experienceId query parameter:
        val queryExperienceId = uri.getQueryParameter("id")

        // Tries to retrieve campaignId query parameter:
        val queryCampaignId = uri.getQueryParameter("campaignID")
        if (uri.scheme == getString(R.string.uri_scheme) && uri.host == "experience" && queryExperienceId != null) {
            startActivity(RoverActivity.makeIntent(packageContext = this, experienceId = queryExperienceId, campaignId = queryCampaignId))
            return
        }

        // If the standard approach above does not meet your needs you can setup any arbitrary URL to launch a Rover
        // experience as long as you can extract the experience ID from it. For example you could use a path based
        // approach which includes the experience ID and optional campaign ID as path components instead of query
        // string parameters. The below example demonstrates how to route URLs in the format
        // `example://experience/<EXPERIENCE_ID>/<CAMPAIGN_ID>` to a Rover experience.
        // Tries to retrieve experienceId query parameter:
        if(uri.scheme == getString(R.string.uri_scheme) && uri.host == "experience" && uri.pathSegments[0] != null) {
            val pathExperienceId: String = uri.pathSegments[0]
            val pathCampaignId: String? = uri.pathSegments.getOrNull(1)
            startActivity(RoverActivity.makeIntent(packageContext = this, experienceId = pathExperienceId, campaignId = pathCampaignId))
        }

        // Universal links are handled similarly:
        // Pass entire URL along to Rover as a universal link to an experience
        if(uri.scheme ?: "" in listOf("http", "https") && uri.host == getString(R.string.associated_domain)) {
            startActivity(RoverActivity.makeIntent(packageContext = this, experienceUrl = uri, campaignId = queryCampaignId))
        }
    }
}
