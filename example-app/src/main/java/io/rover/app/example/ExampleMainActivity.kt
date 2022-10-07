package io.rover.app.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import io.rover.sdk.ui.containers.RoverActivity

class ExampleMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // For the purposes of the example/test app, we'll create a simple bit of UI for manually
        // opening Experience URLs or IDs pasted into a field.
        setContent {
            ManuallyOpenExperiences()
        }

        // Next, we'll show how to route URIs being opened by this Activity to Rover Experiences
        // as applicable.

        // Example of manually starting an experience with an experienceId
        // startActivity(
        //     RoverActivity.makeIntent(this, experienceId = "my-experience-id", campaignId = null)
        // )

        val uri: Uri = intent.data ?: return

        // You will need to setup a specific URL structure to be used for presenting Rover
        // experiences in your app. The simplest approach is to use a specific URL path/host and
        // include the experience ID and (optional) campaign ID as query parameters. The manifest
        // included with this example app and below example code demonstrates how to route URLs in
        // the format `example://experience?id=<EXPERIENCE_ID>&campaignID=<CAMPAIGN_ID>` to a Rover
        // experience.

        // Tries to retrieve experienceId query parameter:
        val queryExperienceId = uri.getQueryParameter("id")

        // Tries to retrieve screenId in order to set the starting screen for the experience
        val queryInitialScreenId = uri.getQueryParameter("screenID")

        // Tries to retrieve campaignId query parameter:
        val queryCampaignId = uri.getQueryParameter("campaignID")

        if (uri.scheme == getString(R.string.uri_scheme) && uri.host == "experience" && queryExperienceId != null) {
            startActivity(RoverActivity.makeIntent(packageContext = this, experienceId = queryExperienceId, campaignId = queryCampaignId, initialScreenId = queryInitialScreenId))
            return
        }

        // If the standard approach above does not meet your needs you can setup any arbitrary URL to launch a Rover
        // experience as long as you can extract the experience ID from it. For example you could use a path based
        // approach which includes the experience ID and optional campaign ID as path components instead of query
        // string parameters. The below example demonstrates how to route URLs in the format
        // `example://experience/<EXPERIENCE_ID>/<CAMPAIGN_ID>` to a Rover experience.
        // Tries to retrieve experienceId query parameter:
        if (uri.scheme == getString(R.string.uri_scheme) && uri.host == "experience" && uri.pathSegments[0] != null) {
            val pathExperienceId: String = uri.pathSegments[0]
            val pathCampaignId: String? = uri.pathSegments.getOrNull(1)
            startActivity(RoverActivity.makeIntent(packageContext = this, experienceId = pathExperienceId, campaignId = pathCampaignId))
        }

        // Universal links are handled similarly:
        // Pass entire URL along to Rover as a universal link to an experience
        if ((uri.scheme ?: "") in listOf(
                "http",
                "https"
            ) && uri.host == getString(R.string.associated_domain)
        ) {
            startActivity(RoverActivity.makeIntent(packageContext = this, experienceUrl = uri, campaignId = queryCampaignId))
        }
    }
}

/**
 * A simple bit of UI for manually opening Experience URLs or IDs pasted into a field in the test app.
 * You do not need this in your own apps.
 */
@Composable
fun ManuallyOpenExperiences() {
    MaterialTheme() {
        Surface() {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val context = LocalContext.current
                    fun openExperienceByUrl(experienceUrl: String) {
                        startActivity(
                            context,
                            RoverActivity.makeIntent(
                                context,
                                experienceUrl = Uri.parse(experienceUrl),
                                campaignId = null
                            ),
                            null
                        )
                    }

                    fun openExperienceById(experienceId: String) {
                        startActivity(
                            context,
                            RoverActivity.makeIntent(
                                context,
                                experienceId = experienceId,
                                campaignId = null
                            ),
                            null
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var urlToLaunch: String by remember { mutableStateOf("") }
                        TextField(
                            value = urlToLaunch,
                            onValueChange = { urlToLaunch = it },
                            maxLines = 1,
                            placeholder = { Text("URL") },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false,
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                if (urlToLaunch.isNotBlank()) {
                                    openExperienceByUrl(urlToLaunch)
                                }
                            }),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )

                        Button(onClick = {
                            if (urlToLaunch.isNotBlank()) {
                                openExperienceByUrl(urlToLaunch)
                            }
                        }) {
                            Text("Open URL", fontSize = 24.sp)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var idToLaunch: String by remember { mutableStateOf("") }
                        TextField(
                            value = idToLaunch,
                            onValueChange = { idToLaunch = it },
                            maxLines = 1,
                            placeholder = { Text("ID") },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false,
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                if (idToLaunch.isNotBlank()) {
                                    openExperienceById(idToLaunch)
                                }
                            }),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )

                        Button(onClick = {
                            if (idToLaunch.isNotBlank()) {
                                openExperienceById(idToLaunch)
                            }
                        }) {
                            Text("Open ID", fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}
