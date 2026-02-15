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

package io.rover.sdk.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rover.sdk.core.Rover
import io.rover.sdk.core.deviceIdentification
import io.rover.sdk.core.platform.getDeviceName
import io.rover.sdk.core.privacy.PrivacyService
import io.rover.sdk.core.trackingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoverSettingsView(dismiss: () -> Unit) {
    val deviceName = remember {
        mutableStateOf(Rover.shared.deviceIdentification.deviceName ?: getDeviceName())
    }

    val debugPreferences = Rover.shared.debugPreferences

    val isTestDevice = remember {
        mutableStateOf(
            debugPreferences.isTestDevice,
        )
    }

    val trackingMode = remember { mutableStateOf(Rover.shared.trackingMode) }

    val deviceIdentifier = remember { mutableStateOf(Rover.shared.deviceIdentification.installationIdentifier) }

    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme,
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(text = "Rover Settings") },
                    navigationIcon = {
                        IconButton(onClick = { dismiss() }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },

                    modifier = Modifier.statusBarsPadding(),
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .padding(paddingValues),
            ) {
                BooleanRow(label = "Test Device", value = isTestDevice.value, updateValue = {
                    isTestDevice.value = it
                    debugPreferences.isTestDevice = it
                })
                TrackingModeRow(value = trackingMode.value, updateValue = {
                    trackingMode.value = it
                    Rover.shared.trackingMode = it
                })
                StringRow(label = "Device Name", value = deviceName.value, updateValue = {
                    deviceName.value = it
                    Rover.shared.deviceIdentification.deviceName = it
                })
                StringRow(label = "Device Identifier", value = deviceIdentifier.value, readOnly = true, updateValue = {
                    deviceIdentifier.value = it
                })
            }
        }
    }
}

@Composable
private fun BooleanRow(label: String, value: Boolean, updateValue: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(checked = value, onCheckedChange = { updateValue(it) })
    }
}

@Composable
private fun StringRow(label: String, value: String, updateValue: (String) -> Unit, readOnly: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                enabled = !readOnly,
                onValueChange = { if (!readOnly) updateValue(it) },
                label = { Text(label) },
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            if (readOnly) {
                val clipboardManager = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                IconButton(onClick = {
                    val clip = ClipData.newPlainText("Device Identifier", value)
                    clipboardManager.setPrimaryClip(clip)
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Copy to clipboard")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackingModeRow(value: PrivacyService.TrackingMode, updateValue: (PrivacyService.TrackingMode) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = value.wireFormat.capitalize(Locale.current),
                onValueChange = {},
                readOnly = true,
                label = { Text("Tracking Mode") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Default") },
                    onClick = {
                        updateValue(PrivacyService.TrackingMode.Default)
                        expanded = false
                    },
                )

                DropdownMenuItem(
                    text = { Text("Anonymized") },
                    onClick = {
                        updateValue(PrivacyService.TrackingMode.Anonymized)
                        expanded = false
                    },
                )
            }
        }
    }
}

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF80DEEA), // Cyan200
    secondary = Color(0xFF90CAF9), // Blue200
)

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xff00BCD4), // Cyan500
    secondary = Color(0xFF90CAF9), // Blue200
)
