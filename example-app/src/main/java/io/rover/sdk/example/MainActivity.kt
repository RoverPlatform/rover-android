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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.fonts.FontStyle
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import io.rover.sdk.core.Rover
import io.rover.sdk.core.permissions.PermissionsNotifierInterface
import io.rover.sdk.debug.RoverDebugActivity
import io.rover.sdk.example.ui.theme.RoverAndroidExampleTheme
import io.rover.sdk.notifications.notificationStore
import io.rover.sdk.notifications.ui.containers.InboxActivity
import kotlinx.coroutines.reactive.asFlow

class MainActivity : ComponentActivity() {
    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            Log.i("RoverExample", "Location permissions result: $permissions")
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    Rover.shared.resolveSingletonOrFail(PermissionsNotifierInterface::class.java)?.permissionGranted(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                }

                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    Rover.shared.resolveSingletonOrFail(PermissionsNotifierInterface::class.java)?.permissionGranted(
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                }
            }
        }

        val backgroundPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { permissionGranted ->
            if (permissionGranted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Rover.shared.resolveSingletonOrFail(PermissionsNotifierInterface::class.java)?.permissionGranted(
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    )
                }
            }
        }

        setContent {
            RoverAndroidExampleTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Dp(8.0f)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TopAppBar(title = { Text(text = "Rover Example App") })

                        Button(onClick = {
                            startActivity(
                                Intent(
                                    this@MainActivity,
                                    RoverDebugActivity::class.java
                                )
                            )
                        }) {
                            Text(text = "Open Rover Settings")
                        }
                        Button(onClick = {
                            startActivity(
                                InboxActivity.makeIntent(this@MainActivity)
                            )
                        }) {
                            Text(text = "Open Rover Inbox")
                        }
                        Button(onClick = {
                            val allGranted = locationPermissions.all {
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    it
                                ) == PackageManager.PERMISSION_GRANTED
                            }

                            if (allGranted) {
                                Log.e("Rover Example", "all permissions already granted")
                                return@Button
                            }

                            locationPermissionRequest.launch(
                                locationPermissions
                            )
                        }) {
                            Text(text = "Request Location Permission")
                        }
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                if (ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    Log.e("Rover Example", "background permission already granted")
                                    return@Button
                                }

                                backgroundPermissionRequest.launch(
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                )
                            }
                        }) {
                            Text(text = "Request Background Location Permission")
                        }

                        var enteredUri by remember { mutableStateOf("") }

                        // edit field for enteredUri:
                        TextField(
                            value = enteredUri,
                            onValueChange = { enteredUri = it },
                            label = { Text("Enter a URL to open") }
                        )

                        // button to open the enteredUri:
                        Button(onClick = {
                            Rover.shared.intentForLink(Uri.parse(enteredUri))?.let {
                                startActivity(it)
                            }
                        }) {
                            Text(text = "Open URL")
                        }

                        Button(onClick = {
                            val intent = FragmentEmbedDemoActivity.createIntent(this@MainActivity)
                            startActivity(intent)
                        }) {
                            Text(text = "Open Embedding Fragment-based Demo Activity")
                        }

                        Button(onClick = {
                            val intent = ComposeEmbedDemoActivity.createIntent(this@MainActivity)
                            startActivity(intent)
                        }) {
                            Text(text = "Open Embedding Compose-based Demo Activity")
                        }

                        Text("Inbox (Notification Store) API demo:", fontWeight = FontWeight.Bold)

                        // The Rover NotificationStore offers several Reactive Streams publishers
                        // that allow you to read back notifications.

                        // You can use these from compose by converting them to Kotlin flows
                        // (using kotlinx-coroutines-reactive) and then using collectAsState().
                        // Be careful to remember the publisher itself, otherwise on all
                        // recompositions it will re-subscribe and spin!

                        val unreadFlow = remember {
                            Rover.shared.notificationStore.unreadCount()
                        }

                        val unreadCount by unreadFlow.asFlow().collectAsState(-1)

                        Text("Unread notifications: $unreadCount")

                        val notificationFlow = remember {
                            Rover.shared.notificationStore.notifications().asFlow()
                        }

                        val notifications by notificationFlow.collectAsState(
                            initial = emptyList()
                        )

                        notifications.forEach { notification ->
                            Text(notification.title ?: "Untitled")
                        }
                    }
                }
            }
        }

        val uri: Uri = intent.data ?: return

        val roverIntent = Rover.shared.intentForLink(
            uri
        )

        if (roverIntent != null) {
            startActivity(roverIntent)
        } else {
            Log.i("RoverExample", "Rover doesn't handle this link: $uri")
        }
    }
}
