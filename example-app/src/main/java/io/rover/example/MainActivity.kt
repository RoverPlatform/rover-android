package io.rover.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import io.rover.Example.ui.theme.RoverCampaignsAndroidExampleTheme
import io.rover.campaigns.core.RoverCampaigns
import io.rover.campaigns.core.permissions.PermissionsNotifierInterface
import io.rover.campaigns.debug.RoverDebugActivity
import io.rover.campaigns.notifications.ui.containers.NotificationCenterActivity

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
                    RoverCampaigns.shared?.resolveSingletonOrFail(PermissionsNotifierInterface::class.java)?.permissionGranted(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                }

                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    RoverCampaigns.shared?.resolveSingletonOrFail(PermissionsNotifierInterface::class.java)?.permissionGranted(
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
                    RoverCampaigns.shared?.resolveSingletonOrFail(PermissionsNotifierInterface::class.java)?.permissionGranted(
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    )
                }
            }
        }

        setContent {
            RoverCampaignsAndroidExampleTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Dp(8.0f)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TopAppBar(title = { Text(text = "Rover Campaigns Example App") })

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
                                NotificationCenterActivity.makeIntent(this@MainActivity)
                            )
                        }) {
                            Text(text = "Open Rover Notification Center")
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
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    RoverCampaignsAndroidExampleTheme {
        Greeting("Android")
    }
}
