/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.example

import android.app.Application
import android.graphics.Color
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import io.rover.example.R
import io.rover.sdk.core.CoreAssembler
import io.rover.sdk.core.Rover
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.debug.DebugAssembler
import io.rover.sdk.experiences.AppThemeDescription
import io.rover.sdk.experiences.ExperiencesAssembler
import io.rover.sdk.experiences.authorize
import io.rover.sdk.experiences.registerCustomActionCallback
import io.rover.sdk.experiences.registerScreenViewedCallback
import io.rover.sdk.location.LocationAssembler
import io.rover.sdk.notifications.NotificationsAssembler
import io.rover.sdk.ticketmaster.TicketmasterAssembler

class ExampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Rover.installSaneGlobalHttpCache(this)

        // Initialize the Rover sdk
        Rover.initialize(
            CoreAssembler(
                accountToken = getString(R.string.rover_api_token),
                application = this,
                urlSchemes = listOf(getString(R.string.rover_uri_scheme)),
                associatedDomains = listOf(getString(R.string.rover_associated_domain))
            ),
            NotificationsAssembler(
                applicationContext = this,
                smallIconResId = R.mipmap.rover_notification_icon
            ) { tokenCallback ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w("RoverExampleApplication", "Fetching FCM registration token failed", task.exception)
                        return@addOnCompleteListener
                    }

                    // Get new FCM registration token
                    val token = task.result
                    tokenCallback(token)
                }
            },
            LocationAssembler(),
            DebugAssembler(),
            TicketmasterAssembler(),
            ExperiencesAssembler(
                // pass in your theme's colors here to enable default styles
                // on certain components:
                appThemeDescription = AppThemeDescription(
                    lightColors = AppThemeDescription.ThemeColors(
                        // use your real theme colors here:
                        primary = Color.RED,
                        onPrimary = Color.GREEN
                    ),
                    darkColors = AppThemeDescription.ThemeColors(
                        // use your real theme colors here:
                        primary = Color.GREEN,
                        onPrimary = Color.RED
                    )
                )
            )
        )

        // This is how you set user info (sometimes thought of as 'custom properties') on
        // Rover. This data is available for customization use inside Experiences, and is also
        // sent through the analytics pipeline so it is available in Audience for segmentation.

        Rover.shared.resolveSingletonOrFail(UserInfoInterface::class.java).update { userInfo ->
            userInfo["first_name"] = "John Doe"
        }

        // You can wire up a callback to be informed of Experience screen views, for example
        // for tracking events into another analytics tool:
        Rover.shared.registerScreenViewedCallback { screenViewed ->
            // track screen view event into your own analytics tools here.
            // a common pattern for naming screens in many is to indicate hierarchy with slashes.  We'll
            // create a screen name out of the experience's name and screen name, if provided.
            val screenName = "${screenViewed.experienceName ?: "Experience"} / ${screenViewed.screenName ?: "Screen"}"

            Log.e("RoverExampleApplication", "Rover experience screen viewed: $screenName")

            // MyAnalyticsSDK.trackScreen(screenName)
        }

        // You can wire up a callback to handle custom actions being activated in Experiences:
        Rover.shared.registerCustomActionCallback { customAction ->
            Log.e("RoverExampleApplication", "Custom Action: $customAction")

            // TODO: implement your custom behaviour here.  You can retrieve `activity`
            //   from the object to launch intents on top of the activity (not available with classic experiences.)
        }

        // You can mutate outgoing URLRequests from data sources in your experiences, allowing your experiences to use authenticated
        // content.
        Rover.shared.authorize("*.apis.myapp.com") { urlRequest ->
            urlRequest.headers["Authorization"] = "mytoken"
        }
    }
}
